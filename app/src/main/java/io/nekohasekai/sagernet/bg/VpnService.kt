/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 * Copyright (C) 2021 by Max Lv <max.c.lv@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.bg

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Network
import android.net.ProxyInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import go.Seq
import io.nekohasekai.sagernet.*
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.StatsEntity
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Subnet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import libcore.*
import java.io.FileDescriptor
import java.io.IOException
import java.net.NetworkInterface
import android.net.VpnService as BaseVpnService

class VpnService : BaseVpnService(),
    BaseService.Interface,
    TrafficListener,
    Protector,
    LocalResolver {

    companion object {
        var instance: VpnService? = null

        const val DEFAULT_MTU = 1500
        const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        const val PRIVATE_VLAN4_GATEWAY = "172.19.0.2"
        const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
        const val PRIVATE_VLAN6_GATEWAY = "fdfe:dcba:9876::2"

        private fun <T> FileDescriptor.use(block: (FileDescriptor) -> T) = try {
            block(this)
        } finally {
            try {
                Os.close(this)
            } catch (_: ErrnoException) {
            }
        }
    }

    lateinit var conn: ParcelFileDescriptor
    var tun: Tun2ray? = null

    private var active = false
    private var metered = false

    @Volatile
    override var underlyingNetwork: Network? = null
        set(value) {
            field = value
            if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(underlyingNetworks)
            }
        }
    private val underlyingNetworks
        get() = // clearing underlyingNetworks makes Android 9 consider the network to be metered
            if (Build.VERSION.SDK_INT == 28 && metered) null else underlyingNetwork?.let {
                arrayOf(it)
            }

    override suspend fun startProcesses() {
        super.startProcesses()
        startVpn()
    }

    override var wakeLock: PowerManager.WakeLock? = null

    @SuppressLint("WakelockTimeout")
    override fun acquireWakeLock() {
        wakeLock = SagerNet.power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sagernet:vpn")
            .apply { acquire() }
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun killProcesses() {
        Libcore.setLocalhostResolver(null)
        tun?.apply {
            close()
        }
        if (::conn.isInitialized) conn.close()
        super.killProcesses()
        persistAppStats()
        active = false
        tun?.apply {
            tun = null
            Seq.destroyRef(refnum)
        }
        GlobalScope.launch(Dispatchers.Default) { DefaultNetworkListener.stop(this) }
    }

    override fun onBind(intent: Intent) = when (intent.action) {
        SERVICE_INTERFACE -> super<BaseVpnService>.onBind(intent)
        else -> super<BaseService.Interface>.onBind(intent)
    }

    override val data = BaseService.Data(this)
    override val tag = "SagerNetVpnService"
    override fun createNotification(profileName: String) =
        ServiceNotification(this, profileName, "service-vpn")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DataStore.serviceMode == Key.MODE_VPN) {
            if (prepare(this) != null) {
                startActivity(
                    Intent(
                        this, VpnRequestActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else return super<BaseService.Interface>.onStartCommand(intent, flags, startId)
        }
        stopRunner()
        return Service.START_NOT_STICKY
    }

    var upstreamInterfaceMTU = 0
    var upstreamInterfaceName: String? = null
    override suspend fun preInit() {
        DefaultNetworkListener.start(this) {
            underlyingNetwork = it
            SagerNet.reloadNetwork(it)
            SagerNet.connectivity.getLinkProperties(it)?.also { link ->
                var mtu = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mtu = link.mtu
                }
                if (mtu == 0) {
                    mtu = NetworkInterface.getByName(link.interfaceName)?.mtu ?: DEFAULT_MTU
                }
                if (upstreamInterfaceMTU != mtu) {
                    upstreamInterfaceMTU = mtu
                    Logs.d("Updated upstream network MTU: $upstreamInterfaceMTU")
                    if (useUpstreamInterfaceMTU && data.state.canStop) forceLoad()
                }
                val oldName = upstreamInterfaceName
                if (oldName != link.interfaceName) {
                    upstreamInterfaceName = link.interfaceName
                }
                if (oldName != null && upstreamInterfaceName != null && oldName != upstreamInterfaceName) {
                    Libcore.resetConnections()
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    Libcore.bindNetworkName(link.interfaceName)
                }
            }
        }
        Libcore.setLocalhostResolver(this)
    }

    inner class NullConnectionException : NullPointerException(),
        BaseService.ExpectedException {
        override fun getLocalizedMessage() = getString(R.string.reboot_required)
    }

    fun getMTU(network: Network): Int {
        var mtu = 0
        SagerNet.connectivity.getLinkProperties(network)?.also { link ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mtu = link.mtu
            }
            if (mtu == 0) {
                mtu = NetworkInterface.getByName(link.interfaceName)?.mtu ?: DEFAULT_MTU
            }
        }
        return mtu
    }

    fun getActiveNetworkUnder23(): Network? {
        val activeInfo = SagerNet.connectivity.activeNetworkInfo ?: return null
        for (network in SagerNet.connectivity.allNetworks) {
            val info = SagerNet.connectivity.getNetworkInfo(network) ?: continue
            if (info.type != activeInfo.type) continue
            if (info.isConnected != activeInfo.isConnected) continue
            if (info.isAvailable != activeInfo.isAvailable) continue
            return network
        }
        return null
    }

    val useUpstreamInterfaceMTU = DataStore.useUpstreamInterfaceMTU

    private fun startVpn() {
        instance = this
        Libcore.setLocalhostResolver(this)

        var mtuFinal = 0
        if (useUpstreamInterfaceMTU) {
            if (upstreamInterfaceMTU > 0) {
                mtuFinal = upstreamInterfaceMTU
                Logs.d("Use MTU of upstream network: $upstreamInterfaceMTU")
            } else {
                val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SagerNet.connectivity.activeNetwork
                } else {
                    getActiveNetworkUnder23()
                }
                if (network != null) {
                    try {
                        mtuFinal = getMTU(network)
                        upstreamInterfaceMTU = mtuFinal
                        Logs.d("Use MTU of upstream network: $mtuFinal")
                    } catch (e: Exception) {
                        Logs.w("Failed to get MTU of current network", e)
                    }
                } else {
                    Logs.d("Failed to get current network")
                }
                if (mtuFinal == 0) {
                    mtuFinal = DataStore.mtu
                }
            }
        } else {
            mtuFinal = DataStore.mtu
            Logs.d("Use MTU: $upstreamInterfaceMTU")
        }

        val profile = data.proxy!!.profile
        val builder = Builder().setConfigureIntent(SagerNet.configureIntent(this))
            .setSession(profile.displayName())
        if (!useUpstreamInterfaceMTU) {
            builder.setMtu(mtuFinal)
        }
        val ipv6Mode = DataStore.ipv6Mode

        builder.addAddress(PRIVATE_VLAN4_CLIENT, 30)

        if (ipv6Mode != IPv6Mode.DISABLE) {
            builder.addAddress(PRIVATE_VLAN6_CLIENT, 126)
        }

        if (DataStore.bypassLan && !DataStore.bypassLanInCoreOnly) {
            resources.getStringArray(R.array.bypass_private_route).forEach {
                val subnet = Subnet.fromString(it)!!
                builder.addRoute(subnet.address.hostAddress!!, subnet.prefixSize)
            }
            builder.addRoute(PRIVATE_VLAN4_GATEWAY, 32)
            // https://issuetracker.google.com/issues/149636790
            if (ipv6Mode != IPv6Mode.DISABLE) {
                builder.addRoute("2000::", 3)
                builder.addRoute(PRIVATE_VLAN6_GATEWAY, 128)
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
            if (ipv6Mode != IPv6Mode.DISABLE) {
                builder.addRoute("::", 0)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder.setUnderlyingNetworks(underlyingNetworks)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(metered)

        val packageName = packageName
        val proxyApps = DataStore.proxyApps
        val tunImplementation = DataStore.tunImplementation
        val needIncludeSelf = tunImplementation == TunImplementation.SYSTEM /*data.proxy!!.config.index.any { !it.isBalancer && it.chain.size > 1 }*/
        val needBypassRootUid = data.proxy!!.config.outboundTagsAll.values.any {
            it.ptBean != null || it.hysteriaBean?.protocol == HysteriaBean.PROTOCOL_FAKETCP
        }
        if (proxyApps || needBypassRootUid) {
            var bypass = DataStore.bypass
            val individual = mutableSetOf<String>()
            val allApps by lazy {
                packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS).filter {
                    when (it.packageName) {
                        packageName -> false
                        "android" -> true
                        else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                    }
                }.map {
                    it.packageName
                }
            }
            if (proxyApps) {
                individual.addAll(DataStore.individual.split('\n').filter { it.isNotBlank() })
                if (bypass && needBypassRootUid) {
                    val individualNew = allApps.toMutableList()
                    individualNew.removeAll(individual)
                    individual.clear()
                    individual.addAll(individualNew)
                    bypass = false
                }
            } else {
                individual.addAll(allApps)
                bypass = false
            }

            individual.apply {
                if (bypass xor needIncludeSelf) add(packageName) else remove(packageName)
            }.forEach {
                try {
                    if (bypass) {
                        builder.addDisallowedApplication(it)
                        Logs.d("Add bypass: $it")
                    } else {
                        builder.addAllowedApplication(it)
                        Logs.d("Add allow: $it")
                    }
                } catch (ex: PackageManager.NameNotFoundException) {
                    Logs.w(ex)
                }
            }
        } else if (!needIncludeSelf) {
            builder.addDisallowedApplication(packageName)
        }

        builder.addDnsServer(PRIVATE_VLAN4_GATEWAY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && DataStore.appendHttpProxy && DataStore.requireHttp) {
            builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOCALHOST, DataStore.httpPort))
        }

        metered = DataStore.meteredNetwork
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(metered)

        conn = builder.establish() ?: throw NullConnectionException()
        active = true   // possible race condition here?

        val config = TunConfig().apply {
            fileDescriptor = conn.fd
            protect = needIncludeSelf
            mtu = mtuFinal
            v2Ray = data.proxy!!.v2rayPoint
            gateway4 = PRIVATE_VLAN4_GATEWAY
            gateway6 = PRIVATE_VLAN6_GATEWAY
            iPv6Mode = ipv6Mode
            implementation = tunImplementation
            sniffing = DataStore.trafficSniffing
            overrideDestination = DataStore.destinationOverride
            debug = DataStore.enableLog
            dumpUID = data.proxy!!.config.dumpUid
            trafficStats = DataStore.appTrafficStatistics
            pCap = DataStore.enablePcap
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bindUpstream = Protector {
                    protect(it)
                    try {
                        val fd = ParcelFileDescriptor.fromFd(it)
                        underlyingNetwork?.bindSocket(fd.fileDescriptor)
                        fd.close()
                    } catch (e: IOException) {
                        Log.e("VpnService", "failed to bind socket to upstream", e)
                    }
                    true
                }
            }

            errorHandler = this@VpnService
            protector = this@VpnService
        }

        tun = Libcore.newTun2ray(config)
    }

    val appStats = mutableListOf<AppStats>()

    override fun updateStats(stats: AppStats) {
        appStats.add(stats)
    }

    fun persistAppStats() {
        if (!DataStore.appTrafficStatistics) return
        val tun = tun ?: return
        appStats.clear()
        tun.readAppTraffics(this)
        val toUpdate = mutableListOf<StatsEntity>()
        val all = SagerDatabase.statsDao.all().associateBy { it.packageName }
        for (stats in appStats) {
            val packageName = if (stats.uid >= 10000) {
                PackageCache.uidMap[stats.uid]?.iterator()?.next() ?: "android"
            } else {
                "android"
            }
            if (!all.containsKey(packageName)) {
                SagerDatabase.statsDao.create(
                    StatsEntity(
                        packageName = packageName,
                        tcpConnections = stats.tcpConnTotal,
                        udpConnections = stats.udpConnTotal,
                        uplink = stats.uplinkTotal,
                        downlink = stats.downlinkTotal
                    )
                )
            } else {
                val entity = all[packageName]!!
                entity.tcpConnections += stats.tcpConnTotal
                entity.udpConnections += stats.udpConnTotal
                entity.uplink += stats.uplinkTotal
                entity.downlink += stats.downlinkTotal
                toUpdate.add(entity)
            }
        }
        if (toUpdate.isNotEmpty()) {
            SagerDatabase.statsDao.update(toUpdate)
        }
    }

    override fun onRevoke() = stopRunner()

    override fun onDestroy() {
        super.onDestroy()
        data.binder.close()
    }


}
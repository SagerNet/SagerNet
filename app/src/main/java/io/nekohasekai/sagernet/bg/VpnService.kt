/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <sekai@neko.services>                    *
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
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.Network
import android.net.ProxyInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.*
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.tun.TunThread
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.utils.Subnet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import android.net.VpnService as BaseVpnService

class VpnService : BaseVpnService(),
    BaseService.Interface {

    companion object {
        var instance: VpnService? = null

        const val VPN_MTU = 1500
        const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        const val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
        const val FAKEDNS_VLAN4_CLIENT = "198.18.0.0"
        const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
        const val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"
        const val FAKEDNS_VLAN6_CLIENT = "fc00::"

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
    lateinit var tun: TunThread

    private var active = false
    private var metered = false

    @Volatile
    private var underlyingNetwork: Network? = null
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1) set(value) {
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

    override fun killProcesses(scope: CoroutineScope) {
        super.killProcesses(scope)
        active = false
        scope.launch { DefaultNetworkListener.stop(this) }
        if (::conn.isInitialized) conn.close()
        if (::tun.isInitialized) tun.interrupt()
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

    override suspend fun preInit() = DefaultNetworkListener.start(this) { underlyingNetwork = it }

    inner class NullConnectionException : NullPointerException(),
        BaseService.ExpectedException {
        override fun getLocalizedMessage() = getString(R.string.reboot_required)
    }

    private suspend fun startVpn() {
        instance = this

        val profile = data.proxy!!.profile
        val builder = Builder().setConfigureIntent(SagerNet.configureIntent(this))
                .setSession(profile.displayName())
                .setMtu(VPN_MTU)
        val useFakeDns = DataStore.enableFakeDns
        val ipv6Mode = DataStore.ipv6Mode
        val useNativeForwarding = DataStore.vpnMode == VpnMode.EXPERIMENTAL_FORWARDING

        builder.addAddress(PRIVATE_VLAN4_CLIENT, 30)
        if (useFakeDns) {
            builder.addAddress(FAKEDNS_VLAN4_CLIENT, 15)
        }

        if (ipv6Mode != IPv6Mode.DISABLE) {
            builder.addAddress(PRIVATE_VLAN6_CLIENT, 126)

            if (useFakeDns) {
                builder.addAddress(FAKEDNS_VLAN6_CLIENT, 18)
            }
        }

        if (DataStore.bypassLan && !DataStore.bypassLanInCoreOnly) {
            resources.getStringArray(R.array.bypass_private_route).forEach {
                val subnet = Subnet.fromString(it)!!
                builder.addRoute(subnet.address.hostAddress, subnet.prefixSize)
            }
            builder.addRoute(PRIVATE_VLAN4_ROUTER, 32)
            // https://issuetracker.google.com/issues/149636790
            if (ipv6Mode != IPv6Mode.DISABLE) {
                builder.addRoute("2000::", 3)
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
        val needBypassRootUid =
            useNativeForwarding || data.proxy!!.config.outboundTagsAll.values.any { it.ptBean != null }
        val needIncludeSelf =
            useNativeForwarding || data.proxy!!.config.index.any { !it.isBalancer && it.chain.size > 1 }
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
        } else {
            builder.addDisallowedApplication(packageName)
        }

        builder.addDnsServer(PRIVATE_VLAN4_ROUTER)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && DataStore.appendHttpProxy && DataStore.requireHttp) {
            builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOCALHOST, DataStore.httpPort))
        }

        metered = DataStore.meteredNetwork
        active = true   // possible race condition here?
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(metered)

        if (useNativeForwarding) builder.setBlocking(useNativeForwarding)
        conn = builder.establish() ?: throw NullConnectionException()

        if (!useNativeForwarding) {
            val cmd = arrayListOf(
                File(applicationInfo.nativeLibraryDir, Executable.TUN2SOCKS).canonicalPath,
                "--netif-ipaddr",
                PRIVATE_VLAN4_ROUTER,
                "--socks-server-addr",
                "$LOCALHOST:${DataStore.socksPort}",
                "--tunmtu",
                VPN_MTU.toString(),
                "--sock-path",
                File(SagerNet.deviceStorage.noBackupFilesDir, "sock_path").canonicalPath,
                "--loglevel",
                "warning"
            )
            cmd += "--dnsgw"
            cmd += "$LOCALHOST:${DataStore.localDNSPort}"
            if (ipv6Mode != IPv6Mode.DISABLE) {
                cmd += "--netif-ip6addr"
                cmd += PRIVATE_VLAN6_ROUTER
            }
            cmd += "--enable-udprelay"
            data.proxy!!.processes.start(cmd, onRestartCallback = {
                try {
                    sendFd(conn.fileDescriptor)
                } catch (e: ErrnoException) {
                    stopRunner(false, e.message)
                }
            })
            sendFd(conn.fileDescriptor)
        } else {
            tun = TunThread(this)
            tun.start()
        }
    }

    private suspend fun sendFd(fd: FileDescriptor) {
        var tries = 0
        val path = File(SagerNet.deviceStorage.noBackupFilesDir, "sock_path").canonicalPath
        while (true) try {
            delay(50L shl tries)
            LocalSocket().use { localSocket ->
                localSocket.connect(
                    LocalSocketAddress(
                        path, LocalSocketAddress.Namespace.FILESYSTEM
                    )
                )
                localSocket.setFileDescriptorsForSend(arrayOf(fd))
                localSocket.outputStream.write(42)
            }
            return
        } catch (e: IOException) {
            if (tries > 5) throw e
            tries += 1
        }
    }

    override fun onRevoke() = stopRunner()

    override fun onDestroy() {
        super.onDestroy()
        data.binder.close()
    }

}
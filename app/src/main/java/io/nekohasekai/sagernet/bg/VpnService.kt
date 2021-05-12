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

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.Network
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.DnsMode
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.utils.Subnet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import android.net.VpnService as BaseVpnService

class VpnService : BaseVpnService(), BaseService.Interface {

    companion object {
        private const val VPN_MTU = 1500
        private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        private const val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
        private const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
        private const val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"

        private fun <T> FileDescriptor.use(block: (FileDescriptor) -> T) = try {
            block(this)
        } finally {
            try {
                Os.close(this)
            } catch (_: ErrnoException) {
            }
        }
    }

    private var conn: ParcelFileDescriptor? = null
    private var active = false
    private var metered = false

    @Volatile
    private var underlyingNetwork: Network? = null
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
        set(value) {
            field = value
            if (active) setUnderlyingNetworks(underlyingNetworks)
        }
    private val underlyingNetworks
        get() =
            // clearing underlyingNetworks makes Android 9 consider the network to be metered
            if (Build.VERSION.SDK_INT == 28 && metered) null else underlyingNetwork?.let {
                arrayOf(it)
            }

    override suspend fun startProcesses() {
        super.startProcesses()
        sendFd(startVpn())
    }

    override fun killProcesses(scope: CoroutineScope) {
        super.killProcesses(scope)
        active = false
        conn?.close()
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
                        this,
                        VpnRequestActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else return super<BaseService.Interface>.onStartCommand(intent, flags, startId)
        }
        stopRunner()
        return Service.START_NOT_STICKY
    }

    override suspend fun preInit() = DefaultNetworkListener.start(this) { underlyingNetwork = it }

    inner class NullConnectionException : NullPointerException(), BaseService.ExpectedException {
        override fun getLocalizedMessage() = getString(R.string.reboot_required)
    }

    private suspend fun startVpn(): FileDescriptor {
        val profile = data.proxy!!.profile
        val builder = Builder()
            .setConfigureIntent(SagerNet.configureIntent(this))
            .setSession(profile.displayName())
            .setMtu(VPN_MTU)
            .addAddress(PRIVATE_VLAN4_CLIENT, 30)
        val useFakeDns = DataStore.dnsMode in arrayOf(DnsMode.FAKEDNS, DnsMode.FAKEDNS_LOCAL)
        /*if (useFakeDns) {
            builder.addAddress("198.18.0.0", 15)
        }*/

        if (DataStore.ipv6Route) {
            builder.addAddress(PRIVATE_VLAN6_CLIENT, 126)

            /*if (useFakeDns) {
                builder.addAddress("fc00::", 18)
            }*/
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder.setUnderlyingNetworks(underlyingNetworks)
        }
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(metered)

        if (DataStore.bypassLan) {
            resources.getStringArray(R.array.bypass_private_route).forEach {
                val subnet = Subnet.fromString(it)!!
                builder.addRoute(subnet.address.hostAddress, subnet.prefixSize)
            }
            builder.addRoute(PRIVATE_VLAN4_ROUTER, 32)
            // https://issuetracker.google.com/issues/149636790
            if (DataStore.ipv6Route) builder.addRoute("2000::", 3)
        } else {
            builder.addRoute("0.0.0.0", 0)
            if (DataStore.ipv6Route) builder.addRoute("::", 0)
        }

        // https://issuetracker.google.com/issues/149636790

        val useSystemDns = DataStore.dnsMode == DnsMode.SYSTEM

        val me = packageName
        if (DataStore.proxyApps) {
            val bypass = DataStore.bypass

            DataStore.individual.split('\n').filter { it.isNotBlank() && it != me }.forEach {
                try {
                    if (bypass) builder.addDisallowedApplication(it)
                    else builder.addAllowedApplication(it)
                } catch (ex: PackageManager.NameNotFoundException) {
                    Logs.w(ex)
                }
            }

            if (bypass) builder.addDisallowedApplication(me)
        } else {
            builder.addDisallowedApplication(me)
        }

        if (useSystemDns) {
            DataStore.systemDns.split("\n").forEach {
                builder.addDnsServer(it)
            }
        } else {
            builder.addDnsServer(PRIVATE_VLAN4_ROUTER)
        }

        metered = DataStore.meteredNetwork
        active = true   // possible race condition here?
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(metered)

        val conn = builder.establish() ?: throw NullConnectionException()
        this.conn = conn

        val cmd =
            arrayListOf(
                File(applicationInfo.nativeLibraryDir, Executable.TUN2SOCKS).canonicalPath,
                "--netif-ipaddr",
                PRIVATE_VLAN4_ROUTER,
                "--socks-server-addr",
                "127.0.0.1:${DataStore.socksPort}",
                "--tunmtu",
                VPN_MTU.toString(),
                "--sock-path",
                File(SagerNet.deviceStorage.noBackupFilesDir, "sock_path").canonicalPath,
                "--loglevel", "warning"
            )
        if (!useSystemDns) {
            cmd += "--dnsgw"
            cmd += "127.0.0.1:${DataStore.localDNSPort}"
        }
        if (DataStore.ipv6Route) {
            cmd += "--netif-ip6addr"
            cmd += PRIVATE_VLAN6_ROUTER
        }
        cmd += "--enable-udprelay"
        data.processes!!.start(cmd, onRestartCallback = {
            try {
                sendFd(conn.fileDescriptor)
            } catch (e: ErrnoException) {
                stopRunner(false, e.message)
            }
        })
        return conn.fileDescriptor
    }

    private suspend fun sendFd(fd: FileDescriptor) {
        var tries = 0
        val path = File(SagerNet.deviceStorage.noBackupFilesDir, "sock_path").canonicalPath
        while (true) try {
            delay(50L shl tries)
            LocalSocket().use { localSocket ->
                localSocket.connect(
                    LocalSocketAddress(
                        path,
                        LocalSocketAddress.Namespace.FILESYSTEM
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
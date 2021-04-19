package io.nekohasekai.sagernet.bg

import android.app.Service
import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerApp
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.VpnRequestActivity
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

        private val PRIVATE_ROUTES = arrayOf(
            "1.0.0.0/8",
            "2.0.0.0/7",
            "4.0.0.0/6",
            "8.0.0.0/7",
            "11.0.0.0/8",
            "12.0.0.0/6",
            "16.0.0.0/4",
            "32.0.0.0/3",
            "64.0.0.0/3",
            "96.0.0.0/6",
            "100.0.0.0/10",
            "100.128.0.0/9",
            "101.0.0.0/8",
            "102.0.0.0/7",
            "104.0.0.0/5",
            "112.0.0.0/10",
            "112.64.0.0/11",
            "112.96.0.0/12",
            "112.112.0.0/13",
            "112.120.0.0/14",
            "112.124.0.0/19",
            "112.124.32.0/21",
            "112.124.40.0/22",
            "112.124.44.0/23",
            "112.124.46.0/24",
            "112.124.48.0/20",
            "112.124.64.0/18",
            "112.124.128.0/17",
            "112.125.0.0/16",
            "112.126.0.0/15",
            "112.128.0.0/9",
            "113.0.0.0/8",
            "114.0.0.0/10",
            "114.64.0.0/11",
            "114.96.0.0/12",
            "114.112.0.0/15",
            "114.114.0.0/18",
            "114.114.64.0/19",
            "114.114.96.0/20",
            "114.114.112.0/23",
            "114.114.115.0/24",
            "114.114.116.0/22",
            "114.114.120.0/21",
            "114.114.128.0/17",
            "114.115.0.0/16",
            "114.116.0.0/14",
            "114.120.0.0/13",
            "114.128.0.0/9",
            "115.0.0.0/8",
            "116.0.0.0/6",
            "120.0.0.0/6",
            "124.0.0.0/7",
            "126.0.0.0/8",
            "128.0.0.0/3",
            "160.0.0.0/5",
            "168.0.0.0/8",
            "169.0.0.0/9",
            "169.128.0.0/10",
            "169.192.0.0/11",
            "169.224.0.0/12",
            "169.240.0.0/13",
            "169.248.0.0/14",
            "169.252.0.0/15",
            "169.255.0.0/16",
            "170.0.0.0/7",
            "172.0.0.0/12",
            "172.32.0.0/11",
            "172.64.0.0/10",
            "172.128.0.0/9",
            "173.0.0.0/8",
            "174.0.0.0/7",
            "176.0.0.0/4",
            "192.0.0.8/29",
            "192.0.0.16/28",
            "192.0.0.32/27",
            "192.0.0.64/26",
            "192.0.0.128/25",
            "192.0.1.0/24",
            "192.0.3.0/24",
            "192.0.4.0/22",
            "192.0.8.0/21",
            "192.0.16.0/20",
            "192.0.32.0/19",
            "192.0.64.0/18",
            "192.0.128.0/17",
            "192.1.0.0/16",
            "192.2.0.0/15",
            "192.4.0.0/14",
            "192.8.0.0/13",
            "192.16.0.0/12",
            "192.32.0.0/11",
            "192.64.0.0/12",
            "192.80.0.0/13",
            "192.88.0.0/18",
            "192.88.64.0/19",
            "192.88.96.0/23",
            "192.88.98.0/24",
            "192.88.100.0/22",
            "192.88.104.0/21",
            "192.88.112.0/20",
            "192.88.128.0/17",
            "192.89.0.0/16",
            "192.90.0.0/15",
            "192.92.0.0/14",
            "192.96.0.0/11",
            "192.128.0.0/11",
            "192.160.0.0/13",
            "192.169.0.0/16",
            "192.170.0.0/15",
            "192.172.0.0/14",
            "192.176.0.0/12",
            "192.192.0.0/10",
            "193.0.0.0/8",
            "194.0.0.0/7",
            "196.0.0.0/7",
            "198.0.0.0/12",
            "198.16.0.0/15",
            "198.20.0.0/14",
            "198.24.0.0/13",
            "198.32.0.0/12",
            "198.48.0.0/15",
            "198.50.0.0/16",
            "198.51.0.0/18",
            "198.51.64.0/19",
            "198.51.96.0/22",
            "198.51.101.0/24",
            "198.51.102.0/23",
            "198.51.104.0/21",
            "198.51.112.0/20",
            "198.51.128.0/17",
            "198.52.0.0/14",
            "198.56.0.0/13",
            "198.64.0.0/10",
            "198.128.0.0/9",
            "199.0.0.0/8",
            "200.0.0.0/7",
            "202.0.0.0/8",
            "203.0.0.0/18",
            "203.0.64.0/19",
            "203.0.96.0/20",
            "203.0.112.0/24",
            "203.0.114.0/23",
            "203.0.116.0/22",
            "203.0.120.0/21",
            "203.0.128.0/17",
            "203.1.0.0/16",
            "203.2.0.0/15",
            "203.4.0.0/14",
            "203.8.0.0/13",
            "203.16.0.0/12",
            "203.32.0.0/11",
            "203.64.0.0/10",
            "203.128.0.0/9",
            "204.0.0.0/6",
            "208.0.0.0/4",
        )

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
                startActivity(Intent(this,
                    VpnRequestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else return super<BaseService.Interface>.onStartCommand(intent, flags, startId)
        }
        stopRunner()
        return Service.START_NOT_STICKY
    }

    inner class NullConnectionException : NullPointerException(), BaseService.ExpectedException {
        override fun getLocalizedMessage() = getString(R.string.reboot_required)
    }

    private suspend fun startVpn(): FileDescriptor {
        val profile = data.proxy!!.profile
        val builder = Builder()
            .setConfigureIntent(SagerApp.configureIntent(this))
            .setSession(profile.displayName())
            .setMtu(VPN_MTU)
            .addAddress(PRIVATE_VLAN4_CLIENT, 30)

        PRIVATE_ROUTES.forEach {
            val subnet = Subnet.fromString(it)!!
            builder.addRoute(subnet.address.hostAddress, subnet.prefixSize)
        }

        builder.addRoute(PRIVATE_VLAN4_ROUTER, 32)
        // https://issuetracker.google.com/issues/149636790
        if (DataStore.ipv6Route) {
            builder.addRoute("2000::", 3)
        }

        /* val proxyApps = when (profile.proxyApps) {
             0 -> DataStore.proxyApps > 0
             1 -> false
             else -> true
         }
         val bypass = when (profile.proxyApps) {
             0 -> DataStore.proxyApps == 2
             3 -> true
             else -> false
         }

         if (proxyApps) {

             val me = packageName
             (profile.individual ?: DataStore.individual ?: "").split('\n')
                 .filter { it.isNotBlank() && it != me }
                 .forEach {
                     try {
                         if (bypass) builder.addDisallowedApplication(it)
                         else builder.addAllowedApplication(it)
                     } catch (ex: PackageManager.NameNotFoundException) {
    //                        Timber.w(ex)
                     }
                 }

         }
    */
        builder.addDisallowedApplication("com.github.shadowsocks")
//        builder.addDisallowedApplication(packageName)

        metered = when (profile.meteredNetwork) {
            0 -> DataStore.meteredNetwork
            1 -> false
            else -> true
        }
        active = true   // possible race condition here?
//        builder.setUnderlyingNetworks(underlyingNetworks)
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(metered)

        val conn = builder.establish() ?: throw NullConnectionException()
        this.conn = conn

        val cmd =
            arrayListOf(File(applicationInfo.nativeLibraryDir, Executable.TUN2SOCKS).canonicalPath,
                "--netif-ipaddr",
                PRIVATE_VLAN4_ROUTER,
                "--socks-server-addr",
                "127.0.0.1:${DataStore.socks5Port}",
                "--tunmtu",
                VPN_MTU.toString(),
                "--sock-path",
                File(SagerApp.deviceStorage.noBackupFilesDir, "sock_path").canonicalPath,
                "--loglevel", "debug")
        if (DataStore.ipv6Route) {
            cmd += "--netif-ip6addr"
            cmd += PRIVATE_VLAN6_ROUTER
        }
        //  cmd += "--enable-udprelay"
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
        val path = File(SagerApp.deviceStorage.noBackupFilesDir, "sock_path").canonicalPath
        while (true) try {
            delay(50L shl tries)
            LocalSocket().use { localSocket ->
                localSocket.connect(LocalSocketAddress(path,
                    LocalSocketAddress.Namespace.FILESYSTEM))
                localSocket.setFileDescriptorsForSend(arrayOf(fd))
                localSocket.outputStream.write(42)
            }
            return
        } catch (e: IOException) {
            if (tries > 5) throw e
            tries += 1
        }
    }


}
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

package io.nekohasekai.sagernet

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Build
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import go.Seq
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.checkMT
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.utils.DeviceStorageApp
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.EpollDatagramChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import libcore.Libcore
import org.conscrypt.Conscrypt
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.tukaani.xz.XZInputStream
import java.io.File
import java.security.Security
import androidx.work.Configuration as WorkConfiguration

class SagerNet : Application(),
    WorkConfiguration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        application = this
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)

        DataStore.init()

        updateNotificationChannels()

        Seq.setContext(this)

        val externalAssets = getExternalFilesDir(null) ?: filesDir
        Libcore.initializeV2Ray(externalAssets.absolutePath + "/", "v2ray/", true)

        runOnDefaultDispatcher {
            externalAssets.mkdirs()
            val geoip = File(externalAssets, "geoip.dat")
            val geoipVersion = File(externalAssets, "geoip.version.txt")
            val geoipVersionInternal = assets.open("v2ray/geoip.version.txt")
                .use { it.bufferedReader().readText() }
            if (!geoip.isFile || DataStore.rulesProvider == 0 && geoipVersion.isFile && geoipVersionInternal.toLong() > geoipVersion.readText()
                    .toLongOrNull() ?: -1L
            ) {
                XZInputStream(assets.open("v2ray/geoip.dat.xz")).use { input ->
                    geoip.outputStream().use {
                        input.copyTo(it)
                    }
                }
                geoipVersion.writeText(geoipVersionInternal)
            }

            val geosite = File(externalAssets, "geosite.dat")
            val geositeVersion = File(externalAssets, "geosite.version.txt")
            val geositeVersionInternal = assets.open("v2ray/geosite.version.txt")
                .use { it.bufferedReader().readText() }
            if (!geosite.isFile || DataStore.rulesProvider == 0 && geositeVersion.isFile && geositeVersionInternal.toLong() > geositeVersion.readText()
                    .toLongOrNull() ?: -1L
            ) {
                XZInputStream(assets.open("v2ray/geosite.dat.xz")).use { input ->
                    geosite.outputStream().use {
                        input.copyTo(it)
                    }
                }
                geositeVersion.writeText(geositeVersionInternal)
            }

            checkMT()
            PackageCache.register()
        }

        Theme.apply(this)
        Theme.applyNightTheme()

        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        try {
            System.loadLibrary("netty_transport_native_epoll")
            serverSocketChannel = EpollServerSocketChannel::class.java
            socketChannel = EpollSocketChannel::class.java
            datagramChannel = EpollDatagramChannel::class.java
            eventLoopGroup = { EpollEventLoopGroup() }
        } catch (e: UnsatisfiedLinkError) {
            Logs.w(e)
            serverSocketChannel = NioServerSocketChannel::class.java
            socketChannel = NioSocketChannel::class.java
            datagramChannel = NioDatagramChannel::class.java
            eventLoopGroup = { NioEventLoopGroup() }
        }

    }

    fun getPackageInfo(packageName: String) = packageManager.getPackageInfo(
        packageName, if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    )!!

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationChannels()
    }

    override fun getWorkManagerConfiguration(): WorkConfiguration {
        return WorkConfiguration.Builder()
            .setDefaultProcessName("${BuildConfig.APPLICATION_ID}:bg")
            .build()
    }

    companion object {

        lateinit var serverSocketChannel: Class<out ServerSocketChannel>
        lateinit var socketChannel: Class<out SocketChannel>
        lateinit var datagramChannel: Class<out DatagramChannel>
        lateinit var eventLoopGroup: () -> EventLoopGroup

        var started = false

        lateinit var application: SagerNet
        val deviceStorage by lazy {
            if (Build.VERSION.SDK_INT < 24) application else DeviceStorageApp(application)
        }

        val configureIntent: (Context) -> PendingIntent by lazy {
            {
                PendingIntent.getActivity(
                    it, 0, Intent(
                        application, MainActivity::class.java
                    ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), PendingIntent.FLAG_IMMUTABLE
                )
            }
        }
        val activity by lazy { application.getSystemService<ActivityManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val user by lazy { application.getSystemService<UserManager>()!! }
        val packageInfo: PackageInfo by lazy { application.getPackageInfo(application.packageName) }
        val directBootSupported by lazy {
            Build.VERSION.SDK_INT >= 24 && try {
                app.getSystemService<DevicePolicyManager>()?.storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
            } catch (_: RuntimeException) {
                false
            }
        }

        val currentProfile get() = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)

        fun getClipboardText(): String {
            return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString() ?: ""
        }

        fun trySetPrimaryClip(clip: String) = try {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, clip))
            true
        } catch (e: RuntimeException) {
            false
        }

        fun updateNotificationChannels() {
            if (Build.VERSION.SDK_INT >= 26) @RequiresApi(26) {
                notification.createNotificationChannels(
                    listOf(
                        NotificationChannel(
                            "service-vpn",
                            application.getText(R.string.service_vpn),
                            if (Build.VERSION.SDK_INT >= 28) NotificationManager.IMPORTANCE_MIN
                            else NotificationManager.IMPORTANCE_LOW
                        ),   // #1355
                        NotificationChannel(
                            "service-proxy",
                            application.getText(R.string.service_proxy),
                            NotificationManager.IMPORTANCE_LOW
                        ), NotificationChannel(
                            "service-subscription",
                            application.getText(R.string.service_subscription),
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                    )
                )
            }
        }

        fun startService() = ContextCompat.startForegroundService(
            application, Intent(application, SagerConnection.serviceClass)
        )

        fun reloadService() =
            application.sendBroadcast(Intent(Action.RELOAD).setPackage(application.packageName))

        fun stopService() =
            application.sendBroadcast(Intent(Action.CLOSE).setPackage(application.packageName))

    }

}
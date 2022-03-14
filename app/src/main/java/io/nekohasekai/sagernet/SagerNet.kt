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

package io.nekohasekai.sagernet

import android.annotation.SuppressLint
import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.StrictMode
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import go.Seq
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.test.DebugInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.utils.CrashHandler
import io.nekohasekai.sagernet.utils.DeviceStorageApp
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import libcore.Libcore
import libcore.UidDumper
import libcore.UidInfo
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.net.InetSocketAddress
import androidx.work.Configuration as WorkConfiguration

class SagerNet : Application(),
    UidDumper,
    WorkConfiguration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
        application = this
    }

    val externalAssets by lazy { getExternalFilesDir(null) ?: filesDir }

    override fun onCreate() {
        super.onCreate()

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        DataStore.init()
        updateNotificationChannels()
        Seq.setContext(this)

        runOnDefaultDispatcher {
            PackageCache.register()
        }

        val isMainProcess = ActivityThread.currentProcessName() == BuildConfig.APPLICATION_ID

        if (!isMainProcess) {
            Libcore.setUidDumper(this, Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            if (BuildConfig.DEBUG) {
                DebugInstance().launch()
            }
        }

        Libcore.setenv("v2ray.conf.geoloader", "memconservative")
        externalAssets.mkdirs()
        Libcore.initializeV2Ray(
            filesDir.absolutePath + "/",
            externalAssets.absolutePath + "/",
            "v2ray/",
            { DataStore.rulesProvider == 0 },
            { DataStore.providerRootCA == RootCAProvider.SYSTEM },
            isMainProcess
        )

        Theme.apply(this)
        Theme.applyNightTheme()

        if (BuildConfig.DEBUG) StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun dumpUid(
        ipProto: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int
    ): Int {
        return SagerNet.connectivity.getConnectionOwnerUid(
            ipProto, InetSocketAddress(srcIp, srcPort), InetSocketAddress(destIp, destPort)
        )
    }

    override fun getUidInfo(uid: Int): UidInfo {
        PackageCache.awaitLoadSync()

        if (uid <= 1000L) {
            val uidInfo = UidInfo()
            uidInfo.label = PackageCache.loadLabel("android")
            uidInfo.packageName = "android"
            return uidInfo
        }

        val packageNames = PackageCache.uidMap[uid.toInt()]
        if (!packageNames.isNullOrEmpty()) for (packageName in packageNames) {
            val uidInfo = UidInfo()
            uidInfo.label = PackageCache.loadLabel(packageName)
            uidInfo.packageName = packageName
            return uidInfo
        }

        error("unknown uid $uid")
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

    @SuppressLint("InlinedApi")
    companion object {

        @Volatile
        var started = false

        lateinit var application: SagerNet

        val isTv by lazy {
            uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }

        val deviceStorage by lazy {
            if (Build.VERSION.SDK_INT < 24) application else DeviceStorageApp(application)
        }

        val configureIntent: (Context) -> PendingIntent by lazy {
            {
                PendingIntent.getActivity(
                    it,
                    0,
                    Intent(
                        application, MainActivity::class.java
                    ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
            }
        }
        val activity by lazy { application.getSystemService<ActivityManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val user by lazy { application.getSystemService<UserManager>()!! }
        val uiMode by lazy { application.getSystemService<UiModeManager>()!! }
        val wifi by lazy { application.getSystemService<WifiManager>()!! }
        val location by lazy { application.getSystemService<LocationManager>()!! }
        val power by lazy { application.getSystemService<PowerManager>()!! }

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
            Logs.w(e)
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

        fun reloadNetwork(network: Network?) {
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return
            val networkType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "data"
            }
            Libcore.setNetworkType(networkType)
            var ssid: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (val transportInfo = capabilities.transportInfo) {
                    is WifiInfo -> {
                        ssid = transportInfo.ssid
                    }
                }
            } else {
                val wifiInfo = wifi.connectionInfo
                ssid = wifiInfo?.ssid
            }
            Libcore.setWifiSSID(ssid?.trim { it == '"' } ?: "")
        }

        fun startService() = ContextCompat.startForegroundService(
            application, Intent(application, SagerConnection.serviceClass)
        )

        fun reloadService() =
            application.sendBroadcast(Intent(Action.RELOAD).setPackage(application.packageName))

        fun stopService() =
            application.sendBroadcast(Intent(Action.CLOSE).setPackage(application.packageName))

    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

}
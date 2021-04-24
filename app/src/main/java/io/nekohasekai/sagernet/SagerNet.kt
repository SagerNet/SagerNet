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
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.utils.DeviceStorageApp
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import libv2ray.Libv2ray
import org.acra.config.mailSender
import org.acra.config.notification
import org.acra.data.StringFormat
import org.acra.file.Directory
import org.acra.ktx.initAcra
import java.io.File

class SagerNet : Application() {

    companion object {
        lateinit var application: SagerNet
        val deviceStorage by lazy {
            if (Build.VERSION.SDK_INT < 24) application else DeviceStorageApp(application)
        }

        val configureIntent: (Context) -> PendingIntent by lazy {
            {
                PendingIntent.getActivity(it, 0, Intent(application, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0)
            }
        }
        val activity by lazy { application.getSystemService<ActivityManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val user by lazy { application.getSystemService<UserManager>()!! }
        val packageInfo: PackageInfo by lazy { application.getPackageInfo(application.packageName) }
        val directBootSupported by lazy {
            Build.VERSION.SDK_INT >= 24 && application.getSystemService<DevicePolicyManager>()?.storageEncryptionStatus ==
                    DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
        }

        val currentProfile get() = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)

        fun getClipboardText(): String {
            val clip = clipboard.primaryClip?.takeIf { it.itemCount > 0 } ?: return ""
            return clip.getItemAt(0).text.toString()
        }

        fun trySetPrimaryClip(clip: String) = try {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, clip))
            true
        } catch (e: RuntimeException) {
            false
        }


        fun updateNotificationChannels() {
            if (Build.VERSION.SDK_INT >= 26) @RequiresApi(26) {
                notification.createNotificationChannels(listOf(
                    NotificationChannel("service-vpn", application.getText(R.string.service_vpn),
                        if (Build.VERSION.SDK_INT >= 28) NotificationManager.IMPORTANCE_MIN
                        else NotificationManager.IMPORTANCE_LOW),   // #1355
                    NotificationChannel("service-proxy",
                        application.getText(R.string.service_proxy),
                        NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel("service-transproxy",
                        application.getText(R.string.service_transproxy),
                        NotificationManager.IMPORTANCE_LOW)))
            }
        }

        fun startService() = ContextCompat.startForegroundService(application,
            Intent(application, SagerConnection.serviceClass))

        fun reloadService() =
            application.sendBroadcast(Intent(Action.RELOAD).setPackage(application.packageName))

        fun stopService() =
            application.sendBroadcast(Intent(Action.CLOSE).setPackage(application.packageName))

    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        application = this

        initAcra {
            alsoReportToAndroidFramework = true

            applicationLogFileDir = Directory.EXTERNAL_FILES
            applicationLogFile = "application_log.txt"
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            sendReportsInDevMode = false
            stopServicesOnCrash = true

            mailSender {
                mailTo = "sekai@neko.services"
                reportAsFile = true
                reportFileName = "crash_report.json"
            }


            notification {
                channelName = "Crash report"
                channelDescription = ":("
            }

        }
    }

    override fun onCreate() {
        super.onCreate()
        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)

        DataStore.init()
        updateNotificationChannels()

        Seq.setContext(applicationContext)
        Libv2ray.setAssetsPath(
            File(application.filesDir, "geofile").absolutePath, "v2ray/"
        )
    }

    fun getPackageInfo(packageName: String) = packageManager.getPackageInfo(packageName,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)!!

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationChannels()
    }

}
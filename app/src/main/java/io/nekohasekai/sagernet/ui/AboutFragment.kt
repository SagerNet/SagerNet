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

package io.nekohasekai.sagernet.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.widget.ListHolderListener
import libv2ray.Libv2ray
import java.io.File
import java.io.IOException
import java.io.PrintWriter


class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view, ListHolderListener)
        toolbar.setTitle(R.string.menu_about)

        parentFragmentManager.beginTransaction().replace(R.id.fragment_holder, AboutContent())
            .commitAllowingStateLoss()

        runOnDefaultDispatcher {
            val license = view.context.assets.open("LICENSE").bufferedReader().readText()
            val licenseText = view.findViewById<TextView>(R.id.license)
            onMainDispatcher {
                licenseText.text = license
                Linkify.addLinks(licenseText, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
            }
        }
    }

    class AboutContent : MaterialAboutFragment() {

        fun exportLog() {
            val context = requireContext()

            runOnDefaultDispatcher {
                val logDir = File(app.cacheDir, "log")
                logDir.mkdir()
                val logFile = File.createTempFile("SagerNet-", ".log", logDir)
                logFile.outputStream().use { out ->
                    PrintWriter(out.bufferedWriter()).use { writer ->
                        writer.println("SagerNet ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on API ${Build.VERSION.SDK_INT}")
                        @Suppress("DEPRECATION")
                        writer.println("ABI ${Build.CPU_ABI} (${Build.SUPPORTED_ABIS.joinToString(", ")})")
                        writer.flush()
                        try {
                            Runtime.getRuntime()
                                .exec(arrayOf("logcat", "-d")).inputStream.use { it.copyTo(out) }
                        } catch (e: IOException) {
                            Logs.w(e)
                            e.printStackTrace(writer)
                        }
                        writer.println()
                    }
                }
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                    .setType("text/x-log")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(context,
                            app.packageName + ".log",
                            logFile)),
                    context.getString(R.string.abc_shareactionprovider_share_with)))
            }
        }

        override fun getMaterialAboutList(activityContext: Context): MaterialAboutList {

            return MaterialAboutList.Builder()
                .addCard(MaterialAboutCard.Builder()
                    .outline(false)
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_baseline_update_24)
                        .text(R.string.app_version)
                        .subText(BuildConfig.VERSION_NAME)
                        .setOnClickAction {
                            requireContext().launchCustomTab(Uri.parse(
                                "https://github.com/SagerNet/SagerNet/releases"
                            ))
                        }
                        .build()
                    )
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_baseline_airplanemode_active_24)
                        .text(getString(R.string.version_x, "v2ray-core"))
                        .subText(Libv2ray.getVersion())
                        .setOnClickAction { }
                        .build()
                    )
                    .apply {
                        for (plugin in PluginManager.fetchPlugins()) {
                            try {
                                addItem(MaterialAboutActionItem.Builder()
                                    .icon(R.drawable.ic_baseline_nfc_24)
                                    .text(getString(R.string.version_x, plugin.id))
                                    .subText("v" + plugin.versionName)
                                    .setOnClickAction {
                                        startActivity(Intent().apply {
                                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                            data =
                                                Uri.fromParts("package", plugin.packageName, null)
                                        })
                                    }
                                    .build()
                                )
                            } catch (e: Exception) {
                                Logs.w(e)
                            }
                        }
                    }
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_baseline_bug_report_24)
                        .text(R.string.logcat)
                        .subText(R.string.logcat_summary)
                        .setOnClickAction { exportLog() }
                        .build()
                    )
                    .build()
                )
                .addCard(MaterialAboutCard.Builder()
                    .outline(false)
                    .title(R.string.project)
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_baseline_sanitizer_24)
                        .text(R.string.github)
                        .setOnClickAction {
                            requireContext().launchCustomTab(Uri.parse(
                                "https://github.com/SagerNet/SagerNet"
                            ))
                        }
                        .build()
                    )
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_qu_shadowsocks_foreground)
                        .text(R.string.telegram)
                        .setOnClickAction {
                            requireContext().launchCustomTab(Uri.parse(
                                "https://t.me/SagerNet"
                            ))
                        }
                        .build())
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_action_copyright)
                        .text(R.string.oss_licenses)
                        .setOnClickAction {
                            startActivity(Intent(context, LicenseActivity::class.java))
                        }
                        .build())
                    .build())
                .build()

        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            view.findViewById<RecyclerView>(R.id.mal_recyclerview).apply {
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            }
        }

    }

}
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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.V2rayBuildResult
import io.nekohasekai.sagernet.fmt.buildV2RayConfig
import io.nekohasekai.sagernet.fmt.buildXrayConfig
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.buildShadowsocksConfig
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.buildShadowsocksRConfig
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.trojan_go.buildTrojanGoConfig
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.plugin.PluginManager.InitResult
import io.nekohasekai.sagernet.utils.DirectBoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import java.io.File
import java.io.IOException
import java.util.*
import io.nekohasekai.sagernet.plugin.PluginManager as PluginManagerS

class ProxyInstance(val profile: ProxyEntity) {

    lateinit var v2rayPoint: V2RayPoint
    lateinit var config: V2rayBuildResult
    lateinit var base: BaseService.Interface
    lateinit var wsForwarder: WebView

    val pluginPath = hashMapOf<String, InitResult>()
    fun initPlugin(name: String): InitResult {
        return pluginPath.getOrPut(name) { PluginManagerS.init(name)!! }
    }

    val pluginConfigs = hashMapOf<Int, String>()

    fun init(service: BaseService.Interface) {
        base = service
        v2rayPoint = Libv2ray.newV2RayPoint(
            SagerSupportClass(
                if (service is VpnService)
                    service else null
            ), false
        )
        val socksPort = DataStore.socksPort + 10
        if (profile.needExternal()) {
            v2rayPoint.domainName = "127.0.0.1:$socksPort"
        } else {
            v2rayPoint.domainName = profile.urlFixed()
        }
        config = buildV2RayConfig(profile)

        val jsonContent = gson.toJson(config.config).also {
            Logs.d(it)
        }

        Libv2ray.testConfig(jsonContent)
        v2rayPoint.configureFileContent = jsonContent
        for (chain in config.index) {
            chain.entries.forEachIndexed { index, (port, profile) ->
                val needChain = index != chain.size - 1
                val bean = profile.requireBean()
                when {
                    profile.useExternalShadowsocks() -> {
                        bean as ShadowsocksBean
                        pluginConfigs[port] = bean.buildShadowsocksConfig(port).also {
                            Logs.d(it)
                        }
                    }
                    bean is ShadowsocksRBean -> {
                        pluginConfigs[port] = profile.requireSSR().buildShadowsocksRConfig().also {
                            Logs.d(it)
                        }
                    }
                    profile.useXray() -> {
                        initPlugin("xtls-plugin")
                        pluginConfigs[port] =
                            gson.toJson(buildXrayConfig(profile, port, needChain, index)).also {
                                Logs.d(it)
                            }
                    }
                    bean is TrojanGoBean -> {
                        initPlugin("trojan-go-plugin")
                        pluginConfigs[port] =
                            bean.buildTrojanGoConfig(port, needChain, index).also {
                                Logs.d(it)
                            }
                    }
                }
            }
        }
    }

    var cacheFiles = ArrayList<File>()

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun start() {

        for (chain in config.index) {
            chain.entries.forEachIndexed { index, (port, profile) ->
                val bean = profile.requireBean()
                val needChain = index != chain.size - 1
                val config = pluginConfigs[port] ?: return@forEachIndexed

                when {
                    profile.useExternalShadowsocks() -> {
                        val context =
                            if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked)
                                SagerNet.application else SagerNet.deviceStorage
                        val configFile =
                            File(
                                context.noBackupFilesDir,
                                "shadowsocks_" + SystemClock.elapsedRealtime() + ".json"
                            )
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            File(
                                SagerNet.application.applicationInfo.nativeLibraryDir,
                                Executable.SS_LOCAL
                            ).absolutePath,
                            "-c", configFile.absolutePath
                        )

                        val env = mutableMapOf<String, String>()

                        if (needChain) {
                            val proxychainsConfigFile =
                                File(
                                    context.noBackupFilesDir,
                                    "proxychains_ss_" + SystemClock.elapsedRealtime() + ".json"
                                )
                            proxychainsConfigFile.writeText("strict_chain\n[ProxyList]\nsocks5 127.0.0.1 ${port + 1}")
                            cacheFiles.add(proxychainsConfigFile)

                            env["LD_PRELOAD"] =
                                File(
                                    SagerNet.application.applicationInfo.nativeLibraryDir,
                                    Executable.PROXYCHAINS
                                ).absolutePath
                            env["PROXYCHAINS_CONF_FILE"] = proxychainsConfigFile.absolutePath
                        }

                        base.data.processes!!.start(commands, env)
                    }
                    bean is ShadowsocksRBean -> {
                        val context =
                            if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked)
                                SagerNet.application else SagerNet.deviceStorage

                        val configFile =
                            File(
                                context.noBackupFilesDir,
                                "shadowsocksr_" + SystemClock.elapsedRealtime() + ".json"
                            )

                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            File(
                                SagerNet.application.applicationInfo.nativeLibraryDir,
                                Executable.SSR_LOCAL
                            ).absolutePath,
                            "-b", "127.0.0.1",
                            "-c", configFile.absolutePath,
                            "-l", "$port"
                        )

                        val env = mutableMapOf<String, String>()

                        if (needChain) {
                            val proxychainsConfigFile =
                                File(
                                    context.noBackupFilesDir,
                                    "proxychains_ssr_" + SystemClock.elapsedRealtime() + ".json"
                                )
                            proxychainsConfigFile.writeText("strict_chain\n[ProxyList]\nsocks5 127.0.0.1 ${port + 1}")
                            cacheFiles.add(proxychainsConfigFile)

                            env["LD_PRELOAD"] =
                                File(
                                    SagerNet.application.applicationInfo.nativeLibraryDir,
                                    Executable.PROXYCHAINS
                                ).absolutePath
                            env["PROXYCHAINS_CONF_FILE"] = proxychainsConfigFile.absolutePath
                        }

                        base.data.processes!!.start(commands, env)
                    }
                    profile.useXray() -> {
                        val context =
                            if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked)
                                SagerNet.application else SagerNet.deviceStorage

                        val configFile =
                            File(
                                context.noBackupFilesDir,
                                "xray_" + SystemClock.elapsedRealtime() + ".json"
                            )
                        configFile.parentFile.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            initPlugin("xtls-plugin").path, "-c", configFile.absolutePath
                        )

                        base.data.processes!!.start(commands)
                    }
                    bean is TrojanGoBean -> {
                        val context =
                            if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked)
                                SagerNet.application else SagerNet.deviceStorage

                        val configFile = File(
                            context.noBackupFilesDir,
                            "trojan_go_" + SystemClock.elapsedRealtime() + ".json"
                        )
                        configFile.parentFile.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            initPlugin("trojan-go-plugin").path, "-config", configFile.absolutePath
                        )

                        base.data.processes!!.start(commands)
                    }
                }
            }
        }

        v2rayPoint.runLoop(DataStore.preferIpv6)

        if (config.requireWs) {
            runOnDefaultDispatcher {
                val url = "http://127.0.0.1:" + (DataStore.socksPort + 1) + "/"
                onMainDispatcher {
                    wsForwarder = WebView(base as Context)
                    @SuppressLint("SetJavaScriptEnabled")
                    wsForwarder.settings.javaScriptEnabled = true
                    wsForwarder.webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            Logs.d("WebView load failed: $error")

                            runOnMainDispatcher {
                                wsForwarder.loadUrl("about:blank")

                                delay(1000L)
                                wsForwarder.loadUrl(url)
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)

                            Logs.d("WebView loaded: ${view.title}")

                        }
                    }
                    wsForwarder.loadUrl(url)
                }
            }
        }

        DataStore.startedProxy = profile.id
    }

    fun stop() {
        v2rayPoint.stopLoop()

        if (::wsForwarder.isInitialized) {
            wsForwarder.loadUrl("about:blank")
            wsForwarder.destroy()
        }

        DataStore.startedProxy = 0L
    }

    fun stats(tag: String, direct: String): Long {
        if (!::v2rayPoint.isInitialized) {
            return 0L
        }
        return v2rayPoint.queryStats(tag, direct)
    }

    val uplinkProxy
        get() = stats("out", "uplink").also {
            uplinkTotalProxy += it
        }

    val downlinkProxy
        get() = stats("out", "downlink").also {
            downlinkTotalProxy += it
        }

    val uplinkDirect
        get() = stats("bypass", "uplink").also {
            uplinkTotalDirect += it
        }

    val downlinkDirect
        get() = stats("bypass", "downlink").also {
            downlinkTotalDirect += it
        }


    var uplinkTotalProxy = 0L
    var downlinkTotalProxy = 0L
    var uplinkTotalDirect = 0L
    var downlinkTotalDirect = 0L

    fun persistStats() {
        try {
            uplinkProxy
            downlinkProxy
            profile.tx += uplinkTotalProxy
            profile.rx += downlinkTotalProxy
            SagerDatabase.proxyDao.updateProxy(profile)
        } catch (e: IOException) {
            if (!DataStore.directBootAware) throw e // we should only reach here because we're in direct boot
            val profile = DirectBoot.getDeviceProfile()!!
            profile.tx += uplinkTotalProxy
            profile.rx += downlinkTotalProxy
            profile.dirty = true
            DirectBoot.update(profile)
            DirectBoot.listenForUnlock()
        }
    }

    fun shutdown(coroutineScope: CoroutineScope) {
        persistStats()
        cacheFiles.removeAll { it.delete(); true }
    }

    private class SagerSupportClass(val service: VpnService?) : V2RayVPNServiceSupportsSet {

        override fun onEmitStatus(p0: Long, status: String): Long {
            Logs.i("onEmitStatus $status")
            return 0L
        }

        override fun protect(l: Long): Boolean {
            return (service ?: return true).protect(l.toInt())
        }

        override fun shutdown(): Long {
            return 0
        }
    }


}
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
import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginManager
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.v2ray.V2rayBuildResult
import io.nekohasekai.sagernet.fmt.v2ray.buildV2RayConfig
import io.nekohasekai.sagernet.fmt.v2ray.buildXrayConfig
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
        v2rayPoint = Libv2ray.newV2RayPoint(SagerSupportClass(if (service is VpnService)
            service else null), false)
        if (profile.useExternalShadowsocks() || profile.useXray() || profile.type == 2) {
            v2rayPoint.domainName = "127.0.0.1:${DataStore.socksPort + 10}"
        } else {
            v2rayPoint.domainName = profile.urlFixed()
        }
        config = buildV2RayConfig(profile)
        v2rayPoint.configureFileContent = gson.toJson(config.config).also {
            Logs.d(it)
        }

        for ((index, profile) in config.index.entries) {
            if (profile.useExternalShadowsocks()) {
                val bean = profile.requireSS()
                val port = DataStore.socksPort + 10 + index

                val proxyConfig = JSONObject().also {
                    it["server"] = bean.serverAddress
                    it["server_port"] = bean.serverPort
                    it["method"] = bean.method
                    it["password"] = bean.password
                    it["local_address"] = "127.0.0.1"
                    it["local_port"] = port
                    it["local_udp_address"] = "127.0.0.1"
                    it["local_udp_port"] = port
                    it["mode"] = "tcp_and_udp"
                    if (DataStore.enableLocalDNS) {
                        it["dns"] = "127.0.0.1:${DataStore.localDNSPort}"
                    } else {
                        it["dns"] = DataStore.remoteDNS
                    }

                    if (DataStore.ipv6Route && DataStore.preferIpv6) {
                        it["ipv6_first"] = true
                    }
                }

                if (bean.plugin.isNotBlank()) {
                    val pluginConfiguration = PluginConfiguration(bean.plugin ?: "")
                    PluginManager.init(pluginConfiguration)?.let { (path, opts, _) ->
                        proxyConfig["plugin"] = path
                        proxyConfig["plugin_opts"] = opts.toString()
                    }
                }

                pluginConfigs[index] = proxyConfig.toStringPretty().also {
                    Logs.d(it)
                }
            } else if (profile.type == 2) {
                val bean = profile.requireSSR()

                val proxyConfig = JSONObject().also {

                    it["server"] = bean.serverAddress
                    it["server_port"] = bean.serverPort
                    it["method"] = bean.method
                    it["password"] = bean.password
                    it["protocol"] = bean.protocol
                    it["protocol_param"] = bean.protocolParam
                    it["obfs"] = bean.obfs
                    it["obfs_param"] = bean.obfsParam
                    it["ipv6"] = DataStore.ipv6Route
                    if (DataStore.enableLocalDNS) {
                        it["dns"] = "127.0.0.1:${DataStore.localDNSPort}"
                    } else {
                        it["dns"] = DataStore.remoteDNS
                    }
                }

                pluginConfigs[index] = proxyConfig.toStringPretty().also {
                    Logs.d(it)
                }
            } else if (profile.useXray()) {
                initPlugin("xtls-plugin")
                pluginConfigs[index] = gson.toJson(buildXrayConfig(profile)).also {
                    Logs.d(it)
                }
            } else if (profile.type == 7) {
                val bean = profile.requireTrojanGo()
                initPlugin("trojan-go-plugin")
                pluginConfigs[index] = JSONObject().also { conf ->
                    conf["run_type"] = "client"
                    conf["local_addr"] = "127.0.0.1"
                    conf["local_port"] = DataStore.socksPort + 10
                    conf["remote_addr"] = bean.serverAddress
                    conf["remote_port"] = bean.serverPort
                    conf["password"] = JSONArray().apply {
                        add(bean.password)
                    }
                    conf["log_level"] = if (BuildConfig.DEBUG) 0 else 2
                    if (DataStore.enableMux) {
                        conf["mux"] = JSONObject().also {
                            it["enabled"] = true
                            it["concurrency"] = DataStore.muxConcurrency
                        }
                    }
                    if (!DataStore.preferIpv6) {
                        conf["tcp"] = JSONObject().also {
                            it["prefer_ipv4"] = true
                        }
                    }

                    when (bean.type) {
                        "original" -> {
                        }
                        "ws" -> {
                            conf["websocket"] = JSONObject().also {
                                it["enabled"] = true
                                it["host"] = bean.host
                                it["path"] = bean.path
                            }
                        }
                    }

                    if (bean.sni.isNotBlank()) {
                        conf["ssl"] = JSONObject().also {
                            it["sni"] = bean.sni
                        }
                    }

                    when {
                        bean.encryption == "none" -> {
                        }
                        bean.encryption.startsWith("ss;") -> {
                            conf["shadowsocks"] = JSONObject().also {
                                it["enabled"] = true
                                it["method"] =
                                    bean.encryption.substringAfter(";").substringBefore(":")
                                it["password"] = bean.encryption.substringAfter(":")
                            }
                        }
                    }

                    if (bean.plugin.isNotBlank()) {
                        val pluginConfiguration = PluginConfiguration(bean.plugin ?: "")
                        PluginManager.init(pluginConfiguration)?.let { (path, opts, isV2) ->
                            conf["transport_plugin"] = JSONObject().also {
                                it["enabled"] = true
                                it["type"] = "shadowsocks"
                                it["command"] = path
                                it["option"] = opts.toString()
                            }
                        }
                    }
                }.also {
                    Logs.d(it.toStringPretty())
                }.toString()
            }
        }
    }

    var cacheFiles = LinkedList<File>()

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun start() {

        for ((index, profile) in config.index.entries) {
            val bean = profile.requireBean()
            val config = pluginConfigs[index] ?: continue

            when {
                profile.useExternalShadowsocks() -> {

                    val context =
                        if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked)
                            SagerNet.application else SagerNet.deviceStorage
                    val configFile =
                        File(context.noBackupFilesDir,
                            "shadowsocks_" + SystemClock.elapsedRealtime() + ".json")
                    configFile.parentFile.mkdirs()
                    configFile.writeText(config)
                    cacheFiles.add(configFile)

                    val commands = mutableListOf(
                        File(SagerNet.application.applicationInfo.nativeLibraryDir,
                            Executable.SS_LOCAL).absolutePath,
                        "-c", configFile.absolutePath
                    )

                    base.data.processes!!.start(commands)
                }
                profile.type == 2 -> {
                    bean as ShadowsocksRBean
                    val port = DataStore.socksPort + 10 + index

                    val context =
                        if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked)
                            SagerNet.application else SagerNet.deviceStorage

                    val configFile =
                        File(context.noBackupFilesDir,
                            "shadowsocksr_" + SystemClock.elapsedRealtime() + ".json")
                    configFile.parentFile.mkdirs()
                    configFile.writeText(config)
                    cacheFiles.add(configFile)

                    val commands = mutableListOf(
                        File(SagerNet.application.applicationInfo.nativeLibraryDir,
                            Executable.SSR_LOCAL).absolutePath,
                        "-b", "127.0.0.1",
                        "-c", configFile.absolutePath,
                        "-l", "$port"
                    )

                    base.data.processes!!.start(commands)
                }
                profile.useXray() -> {
                    val context =
                        if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked)
                            SagerNet.application else SagerNet.deviceStorage

                    val configFile =
                        File(context.noBackupFilesDir,
                            "xray_" + SystemClock.elapsedRealtime() + ".json")
                    configFile.parentFile.mkdirs()
                    configFile.writeText(config)
                    cacheFiles.add(configFile)

                    val commands = mutableListOf(
                        initPlugin("xtls-plugin").path, "-c", configFile.absolutePath
                    )

                    base.data.processes!!.start(commands)
                }
                profile.type == 7 -> {
                    val context =
                        if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked)
                            SagerNet.application else SagerNet.deviceStorage

                    val configFile =
                        File(context.noBackupFilesDir,
                            "trojan_go_" + SystemClock.elapsedRealtime() + ".json")
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
    }

    fun stop() {
        v2rayPoint.stopLoop()

        if (::wsForwarder.isInitialized) {
            wsForwarder.loadUrl("about:blank")
            wsForwarder.destroy()
        }
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
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
import cn.hutool.core.util.NumberUtil
import com.v2ray.core.app.observatory.command.GetOutboundStatusRequest
import com.v2ray.core.app.observatory.command.ObservatoryServiceGrpcKt
import com.v2ray.core.app.stats.command.GetStatsRequest
import com.v2ray.core.app.stats.command.StatsServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.StatusException
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.V2rayBuildResult
import io.nekohasekai.sagernet.fmt.brook.BrookBean
import io.nekohasekai.sagernet.fmt.brook.internalUri
import io.nekohasekai.sagernet.fmt.buildCustomConfig
import io.nekohasekai.sagernet.fmt.buildV2RayConfig
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildNaiveConfig
import io.nekohasekai.sagernet.fmt.pingtunnel.PingTunnelBean
import io.nekohasekai.sagernet.fmt.relaybaton.RelayBatonBean
import io.nekohasekai.sagernet.fmt.relaybaton.buildRelayBatonConfig
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.buildShadowsocksConfig
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.buildShadowsocksRConfig
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.trojan_go.buildCustomTrojanConfig
import io.nekohasekai.sagernet.fmt.trojan_go.buildTrojanGoConfig
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.plugin.PluginManager.InitResult
import io.nekohasekai.sagernet.utils.DirectBoot
import kotlinx.coroutines.*
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import java.io.File
import java.io.IOException
import java.util.*
import io.nekohasekai.sagernet.plugin.PluginManager as PluginManagerS

class ProxyInstance(val profile: ProxyEntity, val service: BaseService.Interface) {

    lateinit var v2rayPoint: V2RayPoint
    lateinit var config: V2rayBuildResult
    lateinit var base: BaseService.Interface
    lateinit var wsForwarder: WebView

    lateinit var managedChannel: ManagedChannel
    val statsService by lazy { StatsServiceGrpcKt.StatsServiceCoroutineStub(managedChannel) }
    val observatoryService by lazy {
        ObservatoryServiceGrpcKt.ObservatoryServiceCoroutineStub(
            managedChannel
        )
    }
    lateinit var observatoryJob: Job

    val pluginPath = hashMapOf<String, InitResult>()
    fun initPlugin(name: String): InitResult {
        return pluginPath.getOrPut(name) { PluginManagerS.init(name)!! }
    }

    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()

    fun init(service: BaseService.Interface) {
        base = service
        v2rayPoint = Libv2ray.newV2RayPoint(
            SagerSupportClass(
                if (service is VpnService) service else null
            ), false
        )
        val socksPort = DataStore.socksPort + 10
        v2rayPoint.domainName = "127.0.0.1:$socksPort"

        if (profile.type != ProxyEntity.TYPE_CONFIG) {
            config = buildV2RayConfig(profile)

            for ((isBalancer, chain) in config.index) {
                chain.entries.forEachIndexed { index, (port, profile) ->
                    val needChain = !isBalancer && index != chain.size - 1
                    val bean = profile.requireBean()
                    when {
                        profile.useExternalShadowsocks() -> {
                            bean as ShadowsocksBean
                            pluginConfigs[port] = profile.type to bean.buildShadowsocksConfig(port)
                        }
                        bean is ShadowsocksRBean -> {
                            pluginConfigs[port] =
                                profile.type to bean.buildShadowsocksRConfig().also {
                                    Logs.d(it)
                                }
                        }
                        bean is TrojanGoBean -> {
                            initPlugin("trojan-go-plugin")
                            pluginConfigs[port] =
                                profile.type to bean.buildTrojanGoConfig(port, needChain, index)
                                    .also {
                                        Logs.d(it)
                                    }
                        }
                        bean is NaiveBean -> {
                            initPlugin("naive-plugin")
                            pluginConfigs[port] = profile.type to bean.buildNaiveConfig(port).also {
                                Logs.d(it)
                            }
                        }
                        bean is PingTunnelBean -> {
                            initPlugin("pingtunnel-plugin")
                        }
                        bean is RelayBatonBean -> {
                            initPlugin("relaybaton-plugin")
                            pluginConfigs[port] =
                                profile.type to bean.buildRelayBatonConfig(port).also {
                                    Logs.d(it)
                                }
                        }
                        bean is BrookBean -> {
                            initPlugin("brook-plugin")
                        }
                    }
                }
            }
        } else {
            val bean = profile.configBean!!

            when (bean.type) {
                "trojan-go" -> {
                    initPlugin("trojan-go-plugin")
                    config = buildV2RayConfig(
                        ProxyEntity(
                            type = ProxyEntity.TYPE_TROJAN_GO,
                            trojanGoBean = TrojanGoBean().applyDefaultValues()
                        )
                    )
                    val (port, _) = config.index[0].second.entries.first()
                    pluginConfigs[port] =
                        profile.type to buildCustomTrojanConfig(bean.content, port)
                } //"v2ray" -> {
                else -> {
                    config = buildCustomConfig(profile)
                }
            }
        }

        Logs.d(config.config)
        v2rayPoint.configureFileContent = config.config
    }

    var cacheFiles = ArrayList<File>()

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        val context =
            if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked) SagerNet.application else SagerNet.deviceStorage

        for ((isBalancer, chain) in config.index) {
            chain.entries.forEachIndexed { index, (port, profile) ->
                val bean = profile.requireBean()
                val needChain = !isBalancer && index != chain.size - 1
                val config = pluginConfigs[port]?.second ?: ""

                when {
                    profile.useExternalShadowsocks() -> {
                        if (needChain) error("shadowsocks-rust is incompatible with chain")

                        val configFile = File(
                            context.noBackupFilesDir,
                            "shadowsocks_" + SystemClock.elapsedRealtime() + ".json"
                        )
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            File(
                                SagerNet.application.applicationInfo.nativeLibraryDir,
                                Executable.SS_LOCAL
                            ).absolutePath, "-c", configFile.absolutePath, "--log-without-time"
                        )

                        if (DataStore.enableLog) commands.add("-v")

                        base.data.processes!!.start(commands)
                    }
                    bean is ShadowsocksRBean -> {
                        val configFile = File(
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
                            "-b",
                            "127.0.0.1",
                            "-c",
                            configFile.absolutePath,
                            "-l",
                            "$port",
                            "-u"
                        )

                        val env = mutableMapOf<String, String>()

                        if (needChain) {
                            val proxychainsConfigFile = File(
                                context.noBackupFilesDir,
                                "proxychains_ssr_" + SystemClock.elapsedRealtime() + ".json"
                            )
                            proxychainsConfigFile.writeText("strict_chain\n[ProxyList]\nsocks5 127.0.0.1 ${port + 1}")
                            cacheFiles.add(proxychainsConfigFile)

                            env["LD_PRELOAD"] = File(
                                SagerNet.application.applicationInfo.nativeLibraryDir,
                                Executable.PROXYCHAINS
                            ).absolutePath
                            env["PROXYCHAINS_CONF_FILE"] = proxychainsConfigFile.absolutePath
                        }

                        base.data.processes!!.start(commands, env)
                    }
                    bean is TrojanGoBean -> {
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
                    bean is NaiveBean -> {
                        val configFile = File(
                            context.noBackupFilesDir,
                            "naive_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            initPlugin("naive-plugin").path, configFile.absolutePath
                        )

                        val env = mutableMapOf<String, String>()

                        if (needChain) {
                            val proxychainsConfigFile = File(
                                context.noBackupFilesDir,
                                "proxychains_naive_" + SystemClock.elapsedRealtime() + ".json"
                            )
                            proxychainsConfigFile.writeText("strict_chain\n[ProxyList]\nsocks5 127.0.0.1 ${port + 1}")
                            cacheFiles.add(proxychainsConfigFile)

                            env["LD_PRELOAD"] = File(
                                SagerNet.application.applicationInfo.nativeLibraryDir,
                                Executable.PROXYCHAINS
                            ).absolutePath
                            env["PROXYCHAINS_CONF_FILE"] = proxychainsConfigFile.absolutePath
                        }

                        base.data.processes!!.start(commands, env)
                    }
                    bean is PingTunnelBean -> {
                        if (needChain) error("PingTunnel is incompatible with chain")

                        val commands = mutableListOf(
                            "su",
                            "-c",
                            initPlugin("pingtunnel-plugin").path,
                            "-type",
                            "client",
                            "-sock5",
                            "1",
                            "-l",
                            "127.0.0.1:$port",
                            "-s",
                            bean.serverAddress
                        )

                        if (bean.key.isNotBlank() && bean.key != "1") {
                            commands.add("-key")
                            commands.add(bean.key)
                        }

                        base.data.processes!!.start(commands)
                    }
                    bean is RelayBatonBean -> {
                        if (needChain) error("RelayBaton is incompatible with chain")

                        val configFile = File(
                            context.noBackupFilesDir,
                            "rb_" + SystemClock.elapsedRealtime() + ".toml"
                        )

                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            initPlugin("relaybaton-plugin").path,
                            "client",
                            "--config",
                            configFile.absolutePath
                        )

                        base.data.processes!!.start(commands)
                    }
                    bean is BrookBean -> {

                        if (needChain) error("brook is incompatible with chain")

                        val commands = mutableListOf(initPlugin("brook-plugin").path)

                        when (bean.protocol) {
                            "ws" -> {
                                commands.add("wsclient")
                                commands.add("--wsserver")
                            }
                            "wss" -> {
                                commands.add("wssclient")
                                commands.add("--wssserver")
                            }
                            else -> {
                                commands.add("client")
                                commands.add("--server")
                            }
                        }

                        commands.add(bean.internalUri())

                        if (bean.password.isNotBlank()) {
                            commands.add("--password")
                            commands.add(bean.password)
                        }

                        commands.add("--socks5")
                        commands.add("127.0.0.1:$port")

                        base.data.processes!!.start(commands)
                    }
                }
            }
        }

        runOnDefaultDispatcher {

            try {
                val start = SystemClock.elapsedRealtime()
                v2rayPoint.runLoop(DataStore.ipv6Mode >= IPv6Mode.PREFER)
                Logs.d("Start v2ray core took ${(SystemClock.elapsedRealtime() - start) / 1000.0}s")
            } catch (e: Throwable) {
                service.stopRunner(
                    false, "${app.getString(R.string.service_failed)}: ${e.readableMessage}"
                )
                return@runOnDefaultDispatcher
            }

            managedChannel = createChannel()

            if (config.observatoryTags.isNotEmpty()) {
                observatoryJob = launch(Dispatchers.IO) {
                    val interval = 10000L
                    while (isActive) {
                        try {
                            val statusList =
                                observatoryService.getOutboundStatus(GetOutboundStatusRequest.getDefaultInstance()).status.statusList
                            if (!isActive) break
                            statusList.forEach { status ->
                                val profileId = status.outboundTag.substringAfter("global-")
                                if (NumberUtil.isLong(profileId)) {
                                    val profile = SagerDatabase.proxyDao.getById(profileId.toLong())
                                    if (profile != null) {
                                        val newStatus = if (status.alive) 1 else 2
                                        val newDelay = status.delay.toInt()
                                        val newErrorReason = status.lastErrorReason

                                        if (profile.status != newStatus || profile.ping != newDelay || profile.error != newErrorReason) {
                                            profile.status = newStatus
                                            profile.ping = newDelay
                                            profile.error = newErrorReason
                                            SagerDatabase.proxyDao.updateProxy(profile)
                                            onMainDispatcher {
                                                service.data.binder.broadcast {
                                                    it.profilePersisted(profile.id)
                                                }
                                            }
                                            Logs.d("Send result for #$profileId ${profile.displayName()}")
                                        }
                                    } else {
                                        Logs.d("Profile with id #$profileId not found")
                                    }
                                } else {
                                    Logs.d("Persist skipped on outbound ${status.outboundTag}")
                                }
                            }
                        } catch (e: StatusException) {
                            Logs.w(e)
                        }
                        delay(interval)
                    }
                }
            }

            if (config.requireWs) {
                runOnDefaultDispatcher {
                    val url = "http://127.0.0.1:" + (DataStore.socksPort + 1) + "/"
                    onMainDispatcher {
                        wsForwarder = WebView(base as Context)
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
    }

    fun stop() {
        runOnDefaultDispatcher {
            DataStore.startedProxy = 0L

            v2rayPoint.stopLoop()
        }
    }

    fun shutdown() {
        persistStats()
        cacheFiles.removeAll { it.delete(); true }

        if (::observatoryJob.isInitialized) {
            observatoryJob.cancel()
        }

        if (::managedChannel.isInitialized) {
            managedChannel.shutdownNow()
        }

        if (::wsForwarder.isInitialized) {
            wsForwarder.loadUrl("about:blank")
            wsForwarder.destroy()
        }
    }

    // ------------- stats -------------

    private suspend fun queryStats(tag: String, direct: String): Long {
        try {
            return queryStatsGrpc(tag, direct)
        } catch (e: StatusException) {
            if (e.status.description?.contains("shutdown") == true) {
                return 0L
            }
            Logs.w(e)
            if (isExpert) return 0L
        }
        return v2rayPoint.queryStats(tag, direct)
    }

    private suspend fun queryStatsGrpc(tag: String, direct: String): Long {
        if (!::managedChannel.isInitialized) {
            return 0L
        }
        try {
            return statsService.getStats(
                GetStatsRequest.newBuilder().setName("outbound>>>$tag>>>traffic>>>$direct")
                    .setReset(true).build()
            ).stat.value
        } catch (e: StatusException) {
            if (e.status.description?.contains("not found") == true) {
                return 0L
            }
            throw e
        }
    }

    private val currentTags by lazy {
        config.outboundTagsAll.filterKeys {
            config.outboundTagsCurrent.contains(
                it
            )
        }
    }

    private val statsTagList by lazy {
        config.outboundTags.toMutableList().apply {
            removeAll(config.outboundTagsCurrent)
        }
    }

    private val statsTags by lazy {
        config.outboundTagsAll.filterKeys {
            statsTagList.contains(
                it
            )
        }
    }

    private val interTags by lazy {
        config.outboundTagsAll.filterValues { it.id != profile.id }
            .filterKeys { !config.outboundTags.contains(it) }
    }

    class OutboundStats(
        val proxyEntity: ProxyEntity, var uplinkTotal: Long = 0L, var downlinkTotal: Long = 0L
    )

    private val statsOutbounds = hashMapOf<Long, OutboundStats>()
    private fun registerStats(
        proxyEntity: ProxyEntity, uplink: Long? = null, downlink: Long? = null
    ) {
        val stats = statsOutbounds.getOrPut(proxyEntity.id) {
            OutboundStats(proxyEntity)
        }
        if (uplink != null) {
            stats.uplinkTotal += uplink
        }
        if (downlink != null) {
            stats.downlinkTotal += downlink
        }
    }

    var uplinkProxy = 0L
    var downlinkProxy = 0L
    var uplinkTotalDirect = 0L
    var downlinkTotalDirect = 0L

    private val outboundStats = OutboundStats(profile)
    suspend fun outboundStats(): Pair<OutboundStats, HashMap<Long, OutboundStats>> {
        if (!::config.isInitialized) {
            return outboundStats to statsOutbounds
        }
        uplinkProxy = 0L
        downlinkProxy = 0L

        val currentUpLink = currentTags.map { (tag, profile) ->
            queryStats(
                tag, "uplink"
            ).also { registerStats(profile, uplink = it) }
        }
        val currentDownLink = currentTags.map { (tag, profile) ->
            queryStats(tag, "downlink").also {
                registerStats(profile, downlink = it)
            }
        }
        uplinkProxy += currentUpLink.fold(0L) { acc, l -> acc + l }
        downlinkProxy += currentDownLink.fold(0L) { acc, l -> acc + l }

        outboundStats.uplinkTotal += uplinkProxy
        outboundStats.downlinkTotal += downlinkProxy

        if (statsTags.isNotEmpty()) {
            uplinkProxy += statsTags.map { (tag, profile) ->
                queryStats(
                    tag, "uplink"
                ).also { registerStats(profile, uplink = it) }
            }.fold(0L) { acc, l -> acc + l }
            downlinkProxy += statsTags.map { (tag, profile) ->
                queryStats(tag, "downlink").also {
                    registerStats(profile, downlink = it)
                }
            }.fold(0L) { acc, l -> acc + l }
        }

        if (interTags.isNotEmpty()) {
            interTags.map { (tag, profile) ->
                queryStats(
                    tag, "uplink"
                ).also { registerStats(profile, uplink = it) }
            }
            interTags.map { (tag, profile) ->
                queryStats(tag, "downlink").also {
                    registerStats(
                        profile, downlink = it
                    )
                }
            }
        }

        return outboundStats to statsOutbounds
    }

    suspend fun directStats(direct: String): Long {
        if (!::config.isInitialized) {
            return 0L
        }
        return queryStats(config.directTag, direct)
    }

    suspend fun uplinkDirect() = directStats("uplink").also {
        uplinkTotalDirect += it
    }

    suspend fun downlinkDirect() = directStats("downlink").also {
        downlinkTotalDirect += it
    }

    fun persistStats() {
        runBlocking {
            try {
                outboundStats()

                val toUpdate = mutableListOf<ProxyEntity>()
                if (outboundStats.uplinkTotal + outboundStats.downlinkTotal != 0L) {
                    profile.tx += outboundStats.uplinkTotal
                    profile.rx += outboundStats.downlinkTotal
                    toUpdate.add(profile)
                }

                statsOutbounds.values.forEach {
                    if (it.uplinkTotal + it.downlinkTotal != 0L) {
                        it.proxyEntity.tx += it.uplinkTotal
                        it.proxyEntity.rx += it.downlinkTotal
                        toUpdate.add(it.proxyEntity)
                    }
                }

                if (toUpdate.isNotEmpty()) {
                    SagerDatabase.proxyDao.updateProxy(toUpdate)
                }
            } catch (e: IOException) {
                if (!DataStore.directBootAware) throw e // we should only reach here because we're in direct boot
                val profile = DirectBoot.getDeviceProfile()!!
                profile.tx += outboundStats.uplinkTotal
                profile.rx += outboundStats.downlinkTotal
                profile.dirty = true
                DirectBoot.update(profile)
                DirectBoot.listenForUnlock()
            }
        }
    }

    private class SagerSupportClass(val service: VpnService?) : V2RayVPNServiceSupportsSet {

        override fun onEmitStatus(status: String) {
            Logs.i("onEmitStatus $status")
        }

        override fun protect(l: Long): Boolean {
            return (service ?: return true).protect(l.toInt())
        }
    }


}
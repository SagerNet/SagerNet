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

package io.nekohasekai.sagernet.utils

import android.content.Context
import android.net.NetworkUtils
import android.os.Build
import android.os.SystemClock
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.Executable
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.LOCALHOST
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
import io.nekohasekai.sagernet.plugin.PluginManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import libv2ray.Libv2ray
import libv2ray.V2RayVPNServiceSupportsSet
import okhttp3.*
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import kotlin.coroutines.Continuation

class TestInstance(val ctx: Context, val profile: ProxyEntity) {

    val httpPort = mkPort()

    val pluginPath = hashMapOf<String, PluginManager.InitResult>()
    fun initPlugin(name: String): PluginManager.InitResult {
        return pluginPath.getOrPut(name) { PluginManager.init(name)!! }
    }

    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()

    object TestSupportsSet : V2RayVPNServiceSupportsSet {
        override fun onEmitStatus(status: String) {
        }

        override fun protect(fd: Long): Boolean {
            return NetworkUtils.protectFromVpn(fd.toInt())
        }
    }

    val point = Libv2ray.newV2RayPoint(TestSupportsSet, false)
    val config = if (profile.type != ProxyEntity.TYPE_CONFIG) {
        buildV2RayConfig(profile, true, httpPort).apply {
            for ((isBalancer, chain) in index) {
                chain.entries.forEachIndexed { index, (port, profile) ->
                    val needChain = !isBalancer && index != chain.size - 1
                    val bean = profile.requireBean()

                    when {
                        profile.useExternalShadowsocks() -> {
                            bean as ShadowsocksBean
                            pluginConfigs[port] = profile.type to bean.buildShadowsocksConfig(port)
                        }
                        bean is ShadowsocksRBean -> {
                            pluginConfigs[port] = profile.type to bean.buildShadowsocksRConfig()
                        }
                        bean is TrojanGoBean -> {
                            initPlugin("trojan-go-plugin")
                            pluginConfigs[port] =
                                profile.type to bean.buildTrojanGoConfig(port, false)
                        }
                        bean is NaiveBean -> {
                            initPlugin("naive-plugin")
                            pluginConfigs[port] = profile.type to bean.buildNaiveConfig(port)
                        }
                        bean is PingTunnelBean -> {
                            initPlugin("pingtunnel-plugin")
                        }
                        bean is RelayBatonBean -> {
                            initPlugin("relaybaton-plugin")
                            pluginConfigs[port] = profile.type to bean.buildRelayBatonConfig(port)
                        }
                        bean is BrookBean -> {
                            initPlugin("brook-plugin")
                        }
                    }
                }
            }
        }
    } else when (profile.configBean!!.type) {
        "trojan-go" -> {
            initPlugin("trojan-go-plugin")
            buildV2RayConfig(
                ProxyEntity(
                    type = ProxyEntity.TYPE_TROJAN_GO,
                    trojanGoBean = TrojanGoBean().applyDefaultValues()
                ), true, httpPort
            ).apply {
                val (pluginPort, _) = index[0].chain.entries.first()
                pluginConfigs[pluginPort] = profile.type to buildCustomTrojanConfig(
                    profile.configBean!!.content, pluginPort
                )
            }
        }
        else -> buildCustomConfig(profile, true, httpPort)
    }

    private lateinit var continuation: Continuation<Int>

    var cacheFiles = ArrayList<File>()
    val processes = GuardedProcessPool {
        Logs.w(it)
        continuation.tryResumeWithException(it)
    }

    lateinit var wsForwarder: WebView

    suspend fun doTest(testTimes: Int): Int {
        return suspendCancellableCoroutine { thiz ->
            continuation = thiz
            runOnDefaultDispatcher {
                runCatching {
                    val context =
                        if (Build.VERSION.SDK_INT < 24 || SagerNet.user.isUserUnlocked) SagerNet.application else SagerNet.deviceStorage

                    for ((isBalancer, chain) in config.index) {
                        chain.entries.forEachIndexed { index, (port, profile) ->
                            val bean = profile.requireBean()
                            val needChain = !isBalancer && index != chain.size - 1
                            val config = pluginConfigs[port]?.second ?: ""

                            when {
                                profile.useExternalShadowsocks() -> {
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
                                        ).absolutePath,
                                        "-c",
                                        configFile.absolutePath,
                                        "--log-without-time"
                                    )

                                    if (DataStore.enableLog) commands.add("-v")

                                    processes.start(commands)
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
                                        LOCALHOST,
                                        "-c",
                                        configFile.absolutePath,
                                        "-l",
                                        "$port",
                                        "-u"
                                    )

                                    processes.start(commands)
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
                                        initPlugin("trojan-go-plugin").path,
                                        "-config",
                                        configFile.absolutePath
                                    )

                                    processes.start(commands)
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

                                    processes.start(commands)
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
                                        "$LOCALHOST:$port",
                                        "-s",
                                        bean.serverAddress
                                    )

                                    if (bean.key.isNotBlank() && bean.key != "1") {
                                        commands.add("-key")
                                        commands.add(bean.key)
                                    }

                                    processes.start(commands)
                                }
                                bean is RelayBatonBean -> {
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

                                    processes.start(commands)
                                }
                                bean is BrookBean -> {
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
                                    commands.add("$LOCALHOST:$port")

                                    processes.start(commands)
                                }
                            }
                        }
                    }

                    point.configureFileContent = config.config
                    point.domainName = "$LOCALHOST:1080"
                    point.runLoop(false)

                    if (config.requireWs) {
                        val url = "http://$LOCALHOST:" + (config.wsPort) + "/"

                        onMainDispatcher {
                            wsForwarder = WebView(ctx)
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

                    val timeout = Duration.ofSeconds(5L)
                    val okHttpClient = OkHttpClient.Builder()
                        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LOCALHOST, httpPort)))
                        .connectTimeout(timeout).callTimeout(timeout).readTimeout(timeout)
                        .writeTimeout(timeout).build()

                    fun newCall(times: Int) = okHttpClient.newCall(
                        Request.Builder().url(DataStore.connectionTestURL)
                            .addHeader("Connection", if (times > 1) "keep-alive" else "close")
                            .addHeader("User-Agent", "curl/7.74.0").build()
                    )

                    newCall(testTimes).enqueue(object : Callback {

                        var times = testTimes
                        var start = if (times == 1) SystemClock.elapsedRealtime() else 0L

                        fun recall() {
                            if (times < 2) start = SystemClock.elapsedRealtime()
                            newCall(times).enqueue(this)
                        }

                        override fun onFailure(call: Call, e: IOException) {
                            destroy()
                            continuation.tryResumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {

                            if (response.isSuccessful && times > 1) {
                                times--
                                recall()
                                return
                            }

                            val elapsed = SystemClock.elapsedRealtime() - start

                            val code = response.code
                            response.closeQuietly()
                            destroy()

                            if (code == 204 || code == 200) {
                                continuation.tryResume(elapsed.toInt())
                            } else {
                                continuation.tryResumeWithException(
                                    IOException(
                                        app.getString(
                                            R.string.connection_test_error_status_code, code
                                        )
                                    )
                                )
                            }

                        }
                    })

                }.onFailure {
                    Logs.w(it)
                    destroy()
                    continuation.tryResumeWithException(it)
                }
            }

        }

    }

    fun destroy() {
        point.shutdown()

        cacheFiles.removeAll { it.delete(); true }

        if (::wsForwarder.isInitialized) {
            wsForwarder.loadUrl("about:blank")
            wsForwarder.destroy()
        }

        runOnMainDispatcher {
            processes.close(this)
        }
    }

}
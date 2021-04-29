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

package io.nekohasekai.sagernet.fmt.v2ray

import cn.hutool.core.codec.Base64
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.*
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import io.nekohasekai.sagernet.ktx.formatObject
import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

const val TAG_SOCKS = "in"
const val TAG_HTTP = "http"
const val TAG_AGENT = "out"
const val TAG_DIRECT = "bypass"
const val TAG_BLOCK = "block"

const val TAG_DNS_IN = "dns-in"
const val TAG_DNS_OUT = "dns-out"

fun buildV2RayConfig(proxy: ProxyEntity): V2RayConfig {

    val bind = if (DataStore.allowAccess) "0.0.0.0" else "127.0.0.1"
    val remoteDns = DataStore.remoteDNS.split(",")
    val domesticDns = DataStore.domesticDns.split(',')
    val enableLocalDNS = DataStore.enableLocalDNS
    val routeChina = DataStore.routeChina
    val trafficSniffing = DataStore.trafficSniffing

    val bean = proxy.requireBean()

    return V2RayConfig().apply {

        dns = DnsObject().apply {
            hosts = mapOf(
                "domain:googleapis.cn" to "googleapis.com"
            )
            servers = mutableListOf()

            servers.addAll(remoteDns.map {
                DnsObject.StringOrServerObject().apply {
                    valueX = it
                }
            })

            if (routeChina == 1) {
                servers.add(DnsObject.StringOrServerObject().apply {
                    valueY = DnsObject.ServerObject().apply {
                        address = domesticDns.first()
                        port = 53
                        domains = listOf("geosite:cn")
                        expectIPs = listOf("geoip:cn")
                    }
                })
            }
        }

        log = LogObject().apply {
            loglevel = if (BuildConfig.DEBUG) "debug" else "warning"
        }

        policy = PolicyObject().apply {
            levels = mapOf("8" to PolicyObject.LevelPolicyObject().apply {
                connIdle = 300
                downlinkOnly = 1
                handshake = 4
                uplinkOnly = 1
            })
            system = PolicyObject.SystemPolicyObject().apply {
                statsOutboundDownlink = true
                statsOutboundUplink = true
            }
        }

        inbounds = mutableListOf()
        inbounds.add(
            InboundObject().apply {
                tag = TAG_SOCKS
                listen = bind
                port = DataStore.socksPort
                protocol = "socks"
                settings = LazyInboundConfigurationObject(
                    SocksInboundConfigurationObject().apply {
                        auth = "noauth"
                        udp = true
                        userLevel = 8
                    })
                if (trafficSniffing) {
                    sniffing = InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = listOf("http", "tls")
                        metadataOnly = false
                    }
                }
            }
        )

        val requireHttp = DataStore.requireHttp

        if (requireHttp) {
            InboundObject().apply {
                tag = TAG_HTTP
                listen = bind
                port = DataStore.httpPort
                protocol = "http"
                settings = LazyInboundConfigurationObject(
                    HTTPInboundConfigurationObject().apply {
                        allowTransparent = true
                        userLevel = 8
                    })
                if (trafficSniffing) {
                    sniffing = InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = listOf("http", "tls")
                        metadataOnly = false
                    }
                }
            }
        }

        outbounds = mutableListOf()
        outbounds.add(
            OutboundObject().apply {
                tag = TAG_AGENT
                if (bean is SOCKSBean) {
                    protocol = "socks"
                    settings = LazyOutboundConfigurationObject(
                        SocksOutboundConfigurationObject().apply {
                            servers = listOf(
                                SocksOutboundConfigurationObject.ServerObject().apply {
                                    address = bean.serverAddress
                                    port = bean.serverPort
                                    if (!bean.username.isNullOrBlank()) {
                                        users =
                                            listOf(SocksOutboundConfigurationObject.ServerObject.UserObject()
                                                .apply {
                                                    user = bean.username
                                                    pass = bean.password
                                                })
                                    }
                                }
                            )
                        })
                    if (bean.tls) {
                        streamSettings = StreamSettingsObject().apply {
                            network = "tcp"
                            security = "tls"
                            if (bean.sni.isNotBlank()) {
                                tlsSettings = TLSObject().apply {
                                    serverName = bean.sni
                                }
                            }
                        }
                    }
                } else if (bean is HttpBean) {
                    protocol = "http"
                    settings = LazyOutboundConfigurationObject(
                        HTTPOutboundConfigurationObject().apply {
                            servers = listOf(
                                HTTPOutboundConfigurationObject.ServerObject().apply {
                                    address = bean.serverAddress
                                    port = bean.serverPort
                                    if (!bean.username.isNullOrBlank()) {
                                        users =
                                            listOf(HTTPInboundConfigurationObject.AccountObject()
                                                .apply {
                                                    user = bean.username
                                                    pass = bean.password
                                                })
                                    }
                                }
                            )
                        })
                    if (bean.tls) {
                        streamSettings = StreamSettingsObject().apply {
                            network = "tcp"
                            security = "tls"
                            if (bean.sni.isNotBlank()) {
                                tlsSettings = TLSObject().apply {
                                    serverName = bean.sni
                                }
                            }
                        }
                    }
                } else if (bean is StandardV2RayBean) {
                    if (bean is VMessBean) {
                        protocol = "vmess"
                        settings = LazyOutboundConfigurationObject(
                            VMessOutboundConfigurationObject().apply {
                                vnext = listOf(
                                    VMessOutboundConfigurationObject.ServerObject().apply {
                                        address = bean.serverAddress
                                        port = bean.serverPort
                                        users = listOf(
                                            VMessOutboundConfigurationObject.ServerObject.UserObject()
                                                .apply {
                                                    id = bean.uuid
                                                    alterId = bean.alterId
                                                    security =
                                                        bean.security.takeIf { it.isNotBlank() }
                                                            ?: "auto"
                                                    level = 8
                                                }
                                        )
                                    }
                                )
                            })
                    } else if (bean is VLESSBean) {
                        protocol = "vless"
                        settings = LazyOutboundConfigurationObject(
                            VLESSOutboundConfigurationObject().apply {
                                vnext = listOf(
                                    VLESSOutboundConfigurationObject.ServerObject().apply {
                                        address = bean.serverAddress
                                        port = bean.serverPort
                                        users = listOf(
                                            VLESSOutboundConfigurationObject.ServerObject.UserObject()
                                                .apply {
                                                    id = bean.uuid
                                                    encryption = bean.encryption
                                                    level = 8
                                                }
                                        )
                                    }
                                )
                            })
                    }

                    streamSettings = StreamSettingsObject().apply {
                        network = bean.type
                        security = bean.security
                        if (security == "tls") {
                            tlsSettings = TLSObject().apply {
                                if (bean.tlsSni.isNotBlank()) {
                                    serverName = bean.tlsSni
                                }
                                if (bean.tlsAlpn.isNotBlank()) {
                                    alpn = bean.tlsAlpn.split(",")
                                }
                            }
                        }

                        when (network) {
                            "tcp" -> {
                                tcpSettings = TcpObject().apply {
                                    if (bean.headerType == "http") {
                                        header = TcpObject.HeaderObject().apply {
                                            type = "http"
                                            if (bean.host.isNotBlank() || bean.path.isNotBlank()) {
                                                request = TcpObject.HeaderObject.HTTPRequestObject()
                                                    .apply {
                                                        headers = mutableMapOf()
                                                        if (bean.host.isNotBlank()) {
                                                            headers["Host"] =
                                                                bean.host.split(",")
                                                                    .map { it.trim() }
                                                        }
                                                        if (bean.path.isNotBlank()) {
                                                            path = bean.path.split(",")
                                                        }
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                            "kcp" -> {
                                kcpSettings = KcpObject().apply {
                                    mtu = 1350
                                    tti = 50
                                    uplinkCapacity = 12
                                    downlinkCapacity = 100
                                    congestion = false
                                    readBufferSize = 1
                                    writeBufferSize = 1
                                    header = KcpObject.HeaderObject().apply {
                                        type = bean.headerType
                                    }
                                    if (bean.path.isNotBlank()) {
                                        seed = bean.path
                                    }
                                }
                            }
                            "ws" -> {
                                wsSettings = WebSocketObject().apply {
                                    headers = mutableMapOf()

                                    if (bean.host.isNotBlank()) {
                                        headers["Host"] = bean.host
                                    }

                                    path = bean.path.takeIf { it.isNotBlank() } ?: "/"

                                    if (bean.wsMaxEarlyData > 0) {
                                        maxEarlyData = bean.wsMaxEarlyData
                                    }

                                    if (bean.wsUseBrowserForwarder) {
                                        useBrowserForwarding = true

                                        browserForwarder = BrowserForwarderObject().apply {
                                            listenAddr = "127.0.0.1"
                                            listenPort = DataStore.socksPort + 11
                                        }
                                    }
                                }
                            }
                            "http", "h2" -> {
                                network = "h2"

                                httpSettings = HttpObject().apply {
                                    if (bean.host.isNotBlank()) {
                                        host = bean.host.split(",")
                                    }

                                    path = bean.path.takeIf { it.isNotBlank() } ?: "/"
                                }
                            }
                            "quic" -> {
                                quicSettings = QuicObject().apply {
                                    security = bean.host.takeIf { it.isNotBlank() } ?: "none"
                                    key = bean.path
                                    header = QuicObject.HeaderObject().apply {
                                        type = bean.headerType.takeIf { it.isNotBlank() } ?: "none"
                                    }
                                }
                            }
                            "grpc" -> {
                                grpcSettings = GrpcObject().apply {
                                    serviceName = bean.path
                                }
                            }
                        }

                    }
                } else if (bean is ShadowsocksBean) {
                    if (!proxy.useExternalShadowsocks()) {
                        protocol = "shadowsocks"
                        settings = LazyOutboundConfigurationObject(
                            ShadowsocksOutboundConfigurationObject().apply {
                                servers = listOf(
                                    ShadowsocksOutboundConfigurationObject.ServerObject()
                                        .apply {
                                            address = bean.serverAddress
                                            port = bean.serverPort
                                            method = bean.method
                                            password = bean.password
                                        }
                                )
                            })
                    } else {
                        protocol = "socks"
                        settings = LazyOutboundConfigurationObject(
                            SocksOutboundConfigurationObject().apply {
                                servers = listOf(
                                    SocksOutboundConfigurationObject.ServerObject().apply {
                                        address = "127.0.0.1"
                                        port = DataStore.socksPort + 10
                                    }
                                )
                            })
                    }
                } else if (bean is TrojanBean) {
                    protocol = "trojan"
                    settings = LazyOutboundConfigurationObject(
                        TrojanOutboundConfigurationObject().apply {
                            servers = listOf(
                                TrojanOutboundConfigurationObject.ServerObject().apply {
                                    address = bean.serverAddress
                                    port = bean.serverPort
                                    password = bean.password
                                    level = 8
                                }
                            )
                        }
                    )
                    streamSettings = StreamSettingsObject().apply {
                        network = "tcp"
                        security = "tls"
                        if (bean.sni.isNotBlank()) {
                            tlsSettings = TLSObject().apply {
                                serverName = bean.sni
                            }
                        }
                    }
                } else {
                    protocol = "socks"
                    settings = LazyOutboundConfigurationObject(
                        SocksOutboundConfigurationObject().apply {
                            servers = listOf(
                                SocksOutboundConfigurationObject.ServerObject().apply {
                                    address = "127.0.0.1"
                                    port = DataStore.socksPort + 10
                                }
                            )
                        })
                }
                if (DataStore.enableMux) {
                    mux = OutboundObject.MuxObject().apply {
                        enabled = true
                        concurrency = DataStore.muxConcurrency
                    }
                }
            }
        )

        outbounds.add(
            OutboundObject().apply {
                tag = TAG_DIRECT
                protocol = "freedom"
            }
        )

        outbounds.add(
            OutboundObject().apply {
                tag = TAG_BLOCK
                protocol = "blackhole"

                settings = LazyOutboundConfigurationObject(
                    BlackholeOutboundConfigurationObject().apply {
                        response = BlackholeOutboundConfigurationObject.ResponseObject().apply {
                            type = "http"
                        }
                    }
                )
            }
        )

        routing = RoutingObject().apply {
            domainStrategy = DataStore.domainStrategy
            domainMatcher = DataStore.domainMatcher

            rules = mutableListOf()

            if (bean is StandardV2RayBean && bean.type == "ws" && bean.wsUseBrowserForwarder == true) {
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_DIRECT
                    Logs.d(formatObject(bean))
                    when {
                        bean.host.isNotBlank() -> domain = listOf(bean.host)
                        bean.serverAddress!!.contains("[a-zA-Z]".toRegex()) -> {
                            domain = listOf(bean.serverAddress)
                        }
                        else -> ip = listOf(bean.serverAddress)
                    }
                })
            }

            if (DataStore.bypassLan) {
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_DIRECT
                    ip = listOf("geoip:private")
                })
            }

            if (DataStore.blockAds) {
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_BLOCK
                    domain = listOf("geosite:category-ads-all")
                })
            }

            if (routeChina > 0) {
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = if (routeChina == 1) TAG_DIRECT else TAG_BLOCK
                    ip = listOf("geoip:cn")
                })
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = if (routeChina == 1) TAG_DIRECT else TAG_BLOCK
                    domain = listOf("geosite:cn")
                })
            }

            rules.add(RoutingObject.RuleObject().apply {
                inboundTag = mutableListOf(TAG_SOCKS)

                if (requireHttp) {
                    inboundTag.add(TAG_HTTP)
                }

                outboundTag = TAG_AGENT
                type = "field"
            })

            rules.add(RoutingObject.RuleObject().apply {
                type = "field"
                outboundTag = TAG_AGENT
                domain = listOf("domain:googleapis.cn")
            })
        }

        if (enableLocalDNS) {
            inbounds.add(
                InboundObject().apply {
                    tag = TAG_DNS_IN
                    listen = "127.0.0.1"
                    port = DataStore.localDNSPort
                    protocol = "dokodemo-door"
                    settings = LazyInboundConfigurationObject(
                        DokodemoDoorInboundConfigurationObject().apply {
                            address = if (remoteDns.first().startsWith("https")) {
                                "1.1.1.1"
                            } else {
                                remoteDns.first()
                            }
                            network = "tcp,udp"
                            port = 53
                        })
                }
            )
            outbounds.add(
                OutboundObject().apply {
                    protocol = "dns"
                    tag = TAG_DNS_OUT
                    settings = LazyOutboundConfigurationObject(
                        DNSOutboundConfigurationObject().apply {
                            network = "tcp"
                        }
                    )
                    proxySettings
                }
            )

            if (!domesticDns.first().startsWith("https")) {
                routing.rules.add(0, RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_DIRECT
                    ip = listOf(domesticDns.first())
                    port = "53"
                })
            }
            if (!remoteDns.first().startsWith("https")) {
                routing.rules.add(0, RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_AGENT
                    ip = listOf(remoteDns.first())
                    port = "53"
                })
            }

            routing.rules.add(0, RoutingObject.RuleObject().apply {
                type = "field"
                inboundTag = listOf(TAG_DNS_IN)
                outboundTag = TAG_DNS_OUT
            })
        }

        stats = emptyMap()

    }

}

fun parseV2Ray(link: String): StandardV2RayBean {
    if (!link.contains("@")) return parseV2RayN(link)

    val bean = if (!link.startsWith("vless://")) {
        VMessBean()
    } else {
        VLESSBean()
    }
    val url = link.replace("vmess://", "https://")
        .replace("vless://", "https://")
        .toHttpUrl()

    bean.serverAddress = url.host
    bean.serverPort = url.port
    bean.name = url.fragment

    if (url.password.isNotBlank()) {
        // https://github.com/v2fly/v2fly-github-io/issues/26
        (bean as VMessBean?) ?: error("Invalid vless url: $link")

        var protocol = url.username
        bean.type = protocol
        bean.alterId = url.password.substringAfterLast('-').toInt()
        bean.uuid = url.password.substringBeforeLast('-')

        if (protocol.endsWith("+tls")) {
            bean.security = "tls"
            protocol = protocol.substring(0, protocol.length - 4)

            url.queryParameter("tlsServerName")?.let {
                if (it.isNotBlank()) {
                    bean.tlsSni = it
                }
            }
        }

        when (protocol) {
            "tcp" -> {
                url.queryParameter("type")?.let {
                    if (it == "http") {
                        bean.headerType = "http"
                        url.queryParameter("host")?.let {
                            bean.host = it
                        }
                    }
                }
            }
            "http" -> {
                url.queryParameter("path")?.let {
                    bean.path = it
                }
                url.queryParameter("host")?.let {
                    bean.host = it.split("|").joinToString(",")
                }
            }
            "ws" -> {
                url.queryParameter("path")?.let {
                    bean.path = it
                }
                url.queryParameter("host")?.let {
                    bean.host = it
                }
            }
            "kcp" -> {
                url.queryParameter("type")?.let {
                    bean.headerType = it
                }
                url.queryParameter("seed")?.let {
                    bean.mKcpSeed = it
                }
            }
            "quic" -> {
                url.queryParameter("security")?.let {
                    bean.quicSecurity = it
                }
                url.queryParameter("key")?.let {
                    bean.quicKey = it
                }
                url.queryParameter("type")?.let {
                    bean.headerType = it
                }
            }
        }
    } else {
        // https://github.com/XTLS/Xray-core/issues/91

        bean.uuid = url.username
        if (url.pathSegments.size > 1 || url.pathSegments[0].isNotBlank()) {
            bean.path = url.pathSegments.joinToString("/")
        }

        val protocol = url.queryParameter("type") ?: error("Missing type parameter")
        bean.type = protocol

        when (url.queryParameter("security")) {
            "tls" -> {
                bean.security = "tls"
                url.queryParameter("sni")?.let {
                    bean.tlsSni = it
                }
                url.queryParameter("alpn")?.let {
                    bean.tlsAlpn = it
                }
            }
        }
        when (protocol) {
            "tcp" -> {
                url.queryParameter("headerType")?.let { headerType ->
                    if (headerType == "http") {
                        bean.headerType = headerType
                        url.queryParameter("host")?.let {
                            bean.host = it
                        }
                        url.queryParameter("path")?.let {
                            bean.path = it
                        }
                    }
                }
            }
            "kcp" -> {
                url.queryParameter("headerType")?.let {
                    bean.headerType = it
                }
                url.queryParameter("seed")?.let {
                    bean.mKcpSeed = it
                }
            }
            "ws", "http" -> {
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let {
                    bean.path = it
                }
            }
            "quic" -> {
                url.queryParameter("headerType")?.let {
                    bean.headerType = it
                }
                url.queryParameter("quicSecurity")?.let { quicSecurity ->
                    bean.quicSecurity = quicSecurity
                    url.queryParameter("key")?.let {
                        bean.quicKey = it
                    }
                }
            }
            "grpc" -> {
                url.queryParameter("serviceName")?.let {
                    bean.grpcServiceName = it
                }
            }
        }
    }

    return bean
}

fun parseV2RayN(link: String): VMessBean {
    val result = link.substringAfter("vmess://").decodeBase64UrlSafe()
    if (result.contains("= vmess")) {
        return parseCsvVMess(result)
    }
    val bean = VMessBean()
    val json = JSONObject(result)

    bean.serverAddress = json.getStr("add")
    bean.serverPort = json.getInt("port")
    bean.security = json.getStr("scy")
    bean.uuid = json.getStr("id")
    bean.alterId = json.getInt("aid")
    bean.type = json.getStr("net")
    bean.headerType = json.getStr("type")
    bean.host = json.getStr("host")
    bean.path = json.getStr("path")
    bean.name = json.getStr("ps")
    bean.tlsSni = json.getStr("sni")
    bean.security = if (json.getStr("tls") == "true") "tls" else ""

    if (json.getInt("v", 2) < 2) {
        when (bean.type) {
            "ws" -> {
                var path = ""
                var host = ""
                val lstParameter = bean.host.split(";")
                if (lstParameter.isNotEmpty()) {
                    path = lstParameter[0].trim()
                }
                if (lstParameter.size > 1) {
                    path = lstParameter[0].trim()
                    host = lstParameter[1].trim()
                }
                bean.path = path
                bean.host = host
            }
            "h2" -> {
                var path = ""
                var host = ""
                val lstParameter = bean.host.split(";")
                if (lstParameter.isNotEmpty()) {
                    path = lstParameter[0].trim()
                }
                if (lstParameter.size > 1) {
                    path = lstParameter[0].trim()
                    host = lstParameter[1].trim()
                }
                bean.path = path
                bean.host = host
            }
        }
    }

    return bean

}

private fun parseCsvVMess(csv: String): VMessBean {

    val args = csv.split(",")

    val bean = VMessBean()

    bean.serverAddress = args[1]
    bean.serverPort = args[2].toInt()
    bean.security = args[3]
    bean.uuid = args[4].replace("\"", "")

    args.subList(5, args.size).forEach {

        when {
            it == "over-tls=true" -> bean.security = "tls"
            it.startsWith("tls-host=") -> bean.host = it.substringAfter("=")
            it.startsWith("obfs=") -> bean.type = it.substringAfter("=")
            it.startsWith("obfs-path=") || it.contains("Host:") -> {
                runCatching {
                    bean.path = it
                        .substringAfter("obfs-path=\"")
                        .substringBefore("\"obfs")
                }
                runCatching {
                    bean.host = it
                        .substringAfter("Host:")
                        .substringBefore("[")
                }

            }

        }

    }

    return bean

}

fun VMessBean.toV2rayN(): String {

    return "vmess://" + JSONObject().also {

        it["v"] = 2
        it["ps"] = name
        it["add"] = serverAddress
        it["port"] = serverPort
        it["id"] = uuid
        it["aid"] = alterId
        it["net"] = type
        it["host"] = host
        it["type"] = headerType
        it["path"] = path
        it["tls"] = if (security == "tls") "true" else ""
        it["sni"] = tlsSni
        it["scy"] = security

    }.toString().let { Base64.encodeUrlSafe(it) }

}

fun StandardV2RayBean.toUri(standard: Boolean): String {
    if (this is VMessBean && alterId > 0) return toV2rayN()

    val builder = HttpUrl.Builder()
        .scheme("https")
        .username(uuid)
        .host(serverAddress)
        .port(serverPort)
        .addQueryParameter("type", type)

    when (type) {
        "tcp" -> {
            if (headerType == "http") {
                builder.addQueryParameter("headerType", headerType)

                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    if (standard) {
                        builder.addQueryParameter("path", path)
                    } else {
                        builder.addPathSegments(path)
                    }
                }
            }
        }
        "kcp" -> {
            if (headerType.isNotBlank() && headerType != "none") {
                builder.addQueryParameter("headerType", headerType)
            }
            if (mKcpSeed.isNotBlank()) {
                builder.addQueryParameter("seed", mKcpSeed)
            }
        }
        "ws", "http" -> {
            if (host.isNotBlank()) {
                builder.addQueryParameter("host", host)
            }
            if (path.isNotBlank()) {
                if (standard) {
                    builder.addQueryParameter("path", path)
                } else {
                    builder.addPathSegments(path)
                }
            }
        }
        "quic" -> {
            if (headerType.isNotBlank() && headerType != "none") {
                builder.addQueryParameter("headerType", headerType)
            }
            if (quicSecurity.isNotBlank() && quicSecurity != "none") {
                builder.addQueryParameter("quicSecurity", quicSecurity)
                builder.addQueryParameter("key", quicKey)
            }
        }
        "grpc" -> {
            if (grpcServiceName.isNotBlank()) {
                builder.addQueryParameter("serviceName", grpcServiceName)
            }
        }
    }

    builder.addQueryParameter("security", security)
    when (security) {
        "tls" -> {
            if (tlsSni.isNotBlank()) {
                builder.addQueryParameter("sni", tlsSni)
            }
            if (tlsAlpn.isNotBlank()) {
                builder.addQueryParameter("alpn", tlsAlpn)
            }
        }
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toString()
        .replace("https://", if (this is VMessBean) "vmess://" else "vless://")
}
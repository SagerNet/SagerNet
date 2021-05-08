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

package io.nekohasekai.sagernet.fmt

import cn.hutool.core.lang.Validator
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.chain.ChainBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.*
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.*
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.formatObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap

const val TAG_SOCKS = "in"
const val TAG_HTTP = "http"
const val TAG_AGENT = "out"
const val TAG_DIRECT = "bypass"
const val TAG_BLOCK = "block"

const val TAG_DNS_IN = "dns-in"
const val TAG_DNS_OUT = "dns-out"

class V2rayBuildResult(
    var config: V2RayConfig,
    var index: LinkedList<LinkedHashMap<Int, ProxyEntity>>,
    var requireWs: Boolean,
)

fun buildV2RayConfig(proxy: ProxyEntity): V2rayBuildResult {

    fun ProxyEntity.resolveChain(): MutableList<ProxyEntity> {
        val bean = requireBean()
        if (bean !is ChainBean) return mutableListOf(this)
        val beans = SagerDatabase.proxyDao.getEntities(bean.proxies)
        val beansMap = beans.map { it.id to it }.toMap()
        val beanList = LinkedList<ProxyEntity>()
        for (proxyId in bean.proxies) {
            beanList.addAll((beansMap[proxyId] ?: continue).resolveChain())
        }
        return beanList
    }

    val proxies = proxy.resolveChain().asReversed()
    val extraRules = SagerDatabase.rulesDao.enabledRules()
    val extraProxies = SagerDatabase.proxyDao.getEntities(extraRules.mapNotNull { rule ->
        rule.outbound.takeIf { it > 0 }
    }.toHashSet().toList()).map { it.id to it.resolveChain() }.toMap()

    val bind = if (DataStore.allowAccess) "0.0.0.0" else "127.0.0.1"
    val remoteDns = DataStore.remoteDNS.split(",")
    val domesticDns = DataStore.domesticDns.split(',')
    val enableLocalDNS = DataStore.enableLocalDNS
    val routeChina = DataStore.routeChina
    val trafficSniffing = DataStore.trafficSniffing
    val indexMap = LinkedList<LinkedHashMap<Int, ProxyEntity>>()
    var requireWs = false

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
                settings =
                    LazyInboundConfigurationObject(this, SocksInboundConfigurationObject().apply {
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
            inbounds.add(InboundObject().apply {
                tag = TAG_HTTP
                listen = bind
                port = DataStore.httpPort
                protocol = "http"
                settings =
                    LazyInboundConfigurationObject(this, HTTPInboundConfigurationObject().apply {
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
            })
        }

        outbounds = mutableListOf()

        val socksPort = DataStore.socksPort

        routing = RoutingObject().apply {
            domainStrategy = DataStore.domainStrategy
            domainMatcher = DataStore.domainMatcher

            rules = mutableListOf()

            val wsRules = HashMap<String, RoutingObject.RuleObject>()

            for (proxyEntity in proxies) {
                val bean = proxyEntity.requireBean()

                if (bean is StandardV2RayBean && bean.type == "ws" && bean.wsUseBrowserForwarder == true) {
                    val route = RoutingObject.RuleObject().apply {
                        type = "field"
                        outboundTag = TAG_DIRECT
                        when {
                            Validator.isIpv4(bean.host) || Validator.isIpv6(bean.host) -> {
                                ip = listOf(bean.host)
                            }
                            bean.host.isNotBlank() -> domain = listOf(bean.host)
                            Validator.isIpv4(bean.serverAddress) || Validator.isIpv6(bean.serverAddress) -> {
                                ip = listOf(bean.serverAddress)
                            }
                            else -> domain = listOf(bean.serverAddress)
                        }
                    }
                    wsRules[bean.host.takeIf { !it.isNullOrBlank() } ?: bean.serverAddress] =
                        route
                }
            }

            rules.addAll(wsRules.values)

            if (DataStore.bypassLan) {
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_DIRECT
                    ip = listOf("geoip:private")
                })
            }
        }

        var currentPort = socksPort + 10
        fun requirePort() = currentPort++

        fun buildChain(tagInbound: String, profileList: List<ProxyEntity>) {
            var pastExternal = false
            lateinit var pastOutbound: OutboundObject
            val chainMap = LinkedHashMap<Int, ProxyEntity>()
            indexMap.add(chainMap)

            profileList.forEachIndexed { index, proxyEntity ->
                Logs.d("Index $index, proxyEntity: ")
                Logs.d(formatObject(proxyEntity))

                val bean = proxyEntity.requireBean()
                val outbound = OutboundObject()

                if (proxyEntity.needExternal()) {
                    val localPort = requirePort()
                    chainMap[localPort] = proxyEntity
                    if (!pastExternal) {
                        outbound.apply {
                            protocol = "socks"
                            settings = LazyOutboundConfigurationObject(this,
                                SocksOutboundConfigurationObject().apply {
                                    servers = listOf(
                                        SocksOutboundConfigurationObject.ServerObject()
                                            .apply {
                                                address = "127.0.0.1"
                                                port = localPort
                                            }
                                    )
                                })
                            tag = if (index == 0) tagInbound else {
                                "$tagInbound-${proxyEntity.id}"
                            }
                            if (index > 0) {
                                pastOutbound.proxySettings =
                                    OutboundObject.ProxySettingsObject().apply {
                                        tag = "$tagInbound-${proxyEntity.id}"
                                        transportLayer = true
                                    }
                            }
                        }
                        pastOutbound = outbound
                        outbounds.add(outbound)
                    }

                    pastExternal = true
                    return@forEachIndexed
                } else {
                    outbound.apply {
                        val keepAliveInterval = DataStore.tcpKeepAliveInterval
                        val needKeepAliveInterval = keepAliveInterval !in intArrayOf(0, 15)

                        if (bean is SOCKSBean) {
                            protocol = "socks"
                            settings = LazyOutboundConfigurationObject(this,
                                SocksOutboundConfigurationObject().apply {
                                    servers = listOf(
                                        SocksOutboundConfigurationObject.ServerObject()
                                            .apply {
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
                            if (bean.tls || needKeepAliveInterval) {
                                streamSettings = StreamSettingsObject().apply {
                                    network = "tcp"
                                    if (bean.tls) {
                                        security = "tls"
                                        if (bean.sni.isNotBlank()) {
                                            tlsSettings = TLSObject().apply {
                                                serverName = bean.sni
                                            }
                                        }
                                    }
                                    if (needKeepAliveInterval) {
                                        sockopt =
                                            StreamSettingsObject.SockoptObject().apply {
                                                tcpKeepAliveInterval = keepAliveInterval
                                            }
                                    }
                                }
                            }
                        } else if (bean is HttpBean) {
                            protocol = "http"
                            settings = LazyOutboundConfigurationObject(this,
                                HTTPOutboundConfigurationObject().apply {
                                    servers = listOf(
                                        HTTPOutboundConfigurationObject.ServerObject()
                                            .apply {
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
                            if (bean.tls || needKeepAliveInterval) {
                                streamSettings = StreamSettingsObject().apply {
                                    network = "tcp"
                                    if (bean.tls) {
                                        security = "tls"
                                        if (bean.sni.isNotBlank()) {
                                            tlsSettings = TLSObject().apply {
                                                serverName = bean.sni
                                            }
                                        }
                                    }
                                    if (needKeepAliveInterval) {
                                        sockopt =
                                            StreamSettingsObject.SockoptObject().apply {
                                                tcpKeepAliveInterval = keepAliveInterval
                                            }
                                    }
                                }
                            }
                        } else if (bean is StandardV2RayBean) {
                            if (bean is VMessBean) {
                                protocol = "vmess"
                                settings = LazyOutboundConfigurationObject(this,
                                    VMessOutboundConfigurationObject().apply {
                                        vnext = listOf(
                                            VMessOutboundConfigurationObject.ServerObject()
                                                .apply {
                                                    address = bean.serverAddress
                                                    port = bean.serverPort
                                                    users = listOf(
                                                        VMessOutboundConfigurationObject.ServerObject.UserObject()
                                                            .apply {
                                                                id = bean.uuidOrGenerate()
                                                                alterId = bean.alterId
                                                                security =
                                                                    bean.encryption.takeIf { it.isNotBlank() }
                                                                        ?: "auto"
                                                                level = 8
                                                            }
                                                    )
                                                }
                                        )
                                    })
                            } else if (bean is VLESSBean) {
                                protocol = "vless"
                                settings = LazyOutboundConfigurationObject(this,
                                    VLESSOutboundConfigurationObject().apply {
                                        vnext = listOf(
                                            VLESSOutboundConfigurationObject.ServerObject()
                                                .apply {
                                                    address = bean.serverAddress
                                                    port = bean.serverPort
                                                    users = listOf(
                                                        VLESSOutboundConfigurationObject.ServerObject.UserObject()
                                                            .apply {
                                                                id = bean.uuidOrGenerate()
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
                                if (bean.security.isNotBlank()) {
                                    security = bean.security
                                }
                                if (security == "tls") {
                                    tlsSettings = TLSObject().apply {
                                        if (bean.sni.isNotBlank()) {
                                            serverName = bean.sni
                                        }
                                        if (bean.alpn.isNotBlank()) {
                                            alpn = bean.alpn.split(",")
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
                                                        request =
                                                            TcpObject.HeaderObject.HTTPRequestObject()
                                                                .apply {
                                                                    headers = mutableMapOf()
                                                                    if (bean.host.isNotBlank()) {
                                                                        headers["Host"] =
                                                                            TcpObject.HeaderObject.StringOrListObject()
                                                                                .apply {
                                                                                    valueY =
                                                                                        bean.host.split(
                                                                                            ","
                                                                                        )
                                                                                            .map { it.trim() }
                                                                                }
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
                                            if (bean.mKcpSeed.isNotBlank()) {
                                                seed = bean.mKcpSeed
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

                                                val pathUrl =
                                                    "http://localhost$path".toHttpUrlOrNull()
                                                if (pathUrl != null) {
                                                    pathUrl.queryParameter("ed")?.let {
                                                        path = pathUrl.newBuilder()
                                                            .removeAllQueryParameters("ed")
                                                            .build()
                                                            .toString()
                                                            .substringAfter("http://localhost")
                                                        earlyDataHeaderName =
                                                            "Sec-WebSocket-Protocol"
                                                    }
                                                }
                                            }

                                            if (bean.wsUseBrowserForwarder) {
                                                useBrowserForwarding = true
                                                requireWs = true
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
                                            security =
                                                bean.quicSecurity.takeIf { it.isNotBlank() }
                                                    ?: "none"
                                            key = bean.quicKey
                                            header = QuicObject.HeaderObject().apply {
                                                type =
                                                    bean.headerType.takeIf { it.isNotBlank() }
                                                        ?: "none"
                                            }
                                        }
                                    }
                                    "grpc" -> {
                                        grpcSettings = GrpcObject().apply {
                                            serviceName = bean.grpcServiceName
                                        }
                                    }
                                }

                                if (needKeepAliveInterval) {
                                    sockopt = StreamSettingsObject.SockoptObject().apply {
                                        tcpKeepAliveInterval = keepAliveInterval
                                    }
                                }

                            }
                        } else if (bean is ShadowsocksBean) {
                            protocol = "shadowsocks"
                            settings = LazyOutboundConfigurationObject(this,
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
                                    if (needKeepAliveInterval) {
                                        streamSettings = StreamSettingsObject().apply {
                                            sockopt =
                                                StreamSettingsObject.SockoptObject().apply {
                                                    tcpKeepAliveInterval = keepAliveInterval
                                                }
                                        }
                                    }
                                })
                        } else if (bean is TrojanBean) {
                            protocol = "trojan"
                            settings = LazyOutboundConfigurationObject(this,
                                TrojanOutboundConfigurationObject().apply {
                                    servers = listOf(
                                        TrojanOutboundConfigurationObject.ServerObject()
                                            .apply {
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
                                if (needKeepAliveInterval) {
                                    sockopt = StreamSettingsObject.SockoptObject().apply {
                                        tcpKeepAliveInterval = keepAliveInterval
                                    }
                                }
                            }
                        }
                        if (index == 0 && proxyEntity.needCoreMux() && DataStore.enableMux) {
                            mux = OutboundObject.MuxObject().apply {
                                enabled = true
                                concurrency = DataStore.muxConcurrency
                            }
                        }
                        tag = if (index == 0) tagInbound else "$tagInbound-${proxyEntity.id}"
                        if (pastExternal) {
                            val localPort = requirePort()
                            chainMap[localPort] = proxyEntity
                            inbounds.add(InboundObject().apply {
                                tag = "$tagInbound-${proxyEntity.id}-in"
                                listen = "127.0.0.1"
                                port = localPort
                                protocol = "socks"
                                settings = LazyInboundConfigurationObject(this,
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
                            })
                            routing.rules.add(RoutingObject.RuleObject().apply {
                                type = "field"
                                inboundTag = listOf("$tagInbound-${proxyEntity.id}-in")
                                outboundTag = "$tagInbound-${proxyEntity.id}"
                            })
                        } else if (index > 0) {
                            pastOutbound.proxySettings =
                                OutboundObject.ProxySettingsObject().apply {
                                    tag = "$tagInbound-${proxyEntity.id}"
                                    transportLayer = true
                                }
                        }
                    }

                    pastExternal = false
                    pastOutbound = outbound
                    outbounds.add(outbound)
                }
            }
        }

        buildChain(TAG_AGENT, proxies)
        extraProxies.forEach { (id, entities) ->
            buildChain("$TAG_AGENT-$id", entities)
        }
        extraRules.forEach { rule ->
            routing.rules.add(RoutingObject.RuleObject().apply {
                type = "field"
                if (rule.domains.isNotBlank()) {
                    domain = rule.domains.split("\n")
                }
                if (rule.ip.isNotBlank()) {
                    ip = rule.ip.split("\n")
                }
                if (rule.port.isNotBlank()) {
                    port = rule.port
                }
                if (rule.sourcePort.isNotBlank()) {
                    sourcePort = rule.sourcePort
                }
                if (rule.network.isNotBlank()) {
                    network = rule.network
                }
                if (rule.source.isNotBlank()) {
                    source = rule.source.split("\n")
                }
                if (rule.protocol.isNotBlank()) {
                    protocol = rule.protocol.split("\n")
                }
                if (rule.attrs.isNotBlank()) {
                    attrs = rule.attrs
                }
                outboundTag = when (val outId = rule.outbound) {
                    0L -> TAG_AGENT
                    -1L -> TAG_DIRECT
                    -2L -> TAG_BLOCK
                    else -> "$TAG_AGENT-$outId"
                }
            })
        }

        val bypassIP = HashSet<String>()
        val bypassDomain = HashSet<String>()
        for (bypassRule in extraRules.filter { it.isBypassRule() }) {
            if (bypassRule.domains.isNotBlank()) {
                bypassDomain.addAll(bypassRule.domains.split("\n"))
            } else if (bypassRule.ip.isNotBlank()) {
                bypassIP.addAll(bypassRule.ip.split("\n"))
            }
        }
        if (bypassIP.isNotEmpty() || bypassDomain.isNotEmpty()) {
            dns.servers.add(DnsObject.StringOrServerObject().apply {
                valueY = DnsObject.ServerObject().apply {
                    address = domesticDns.first()
                    port = 53
                    if (bypassIP.isNotEmpty()) {
                        expectIPs = bypassIP.toList()
                    }
                    if (bypassDomain.isNotEmpty()) {
                        domains = bypassDomain.toList()
                    }
                }
            })
        }

        if (requireWs) {
            browserForwarder = BrowserForwarderObject().apply {
                listenAddr = "127.0.0.1"
                listenPort = DataStore.socksPort + 1
            }
        }

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

                settings = LazyOutboundConfigurationObject(this,
                    BlackholeOutboundConfigurationObject().apply {
                        response =
                            BlackholeOutboundConfigurationObject.ResponseObject()
                                .apply {
                                    type = "http"
                                }
                    }
                )
            }
        )

        if (enableLocalDNS) {
            inbounds.add(
                InboundObject().apply {
                    tag = TAG_DNS_IN
                    listen = "127.0.0.1"
                    port = DataStore.localDNSPort
                    protocol = "dokodemo-door"
                    settings = LazyInboundConfigurationObject(this,
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
                    settings = LazyOutboundConfigurationObject(this,
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

    }.let {
        V2rayBuildResult(
            it,
            indexMap,
            requireWs
        )
    }

}

fun buildXrayConfig(proxy: ProxyEntity, localPort: Int, chain: Boolean, index: Int): V2RayConfig {

    val remoteDns = DataStore.remoteDNS.split(",")
    val trafficSniffing = DataStore.trafficSniffing
    val bean = proxy.requireBean()

    return V2RayConfig().apply {

        dns = DnsObject().apply {
            servers = mutableListOf()

            if (!DataStore.enableLocalDNS) {
                servers.addAll(remoteDns.map {
                    DnsObject.StringOrServerObject().apply {
                        valueX = it
                    }
                })
            } else {
                servers.add(
                    DnsObject.StringOrServerObject().apply {
                        valueY = DnsObject.ServerObject().apply {
                            address = "127.0.0.1"
                            port = DataStore.localDNSPort
                        }
                    }
                )
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
                listen = "127.0.0.1"
                port = localPort
                protocol = "socks"
                settings =
                    LazyInboundConfigurationObject(this, SocksInboundConfigurationObject().apply {
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

        outbounds = mutableListOf()
        val outbound = OutboundObject().apply {
            tag = TAG_AGENT
            if (bean is StandardV2RayBean) {
                bean as VLESSBean
                protocol = "vless"
                settings = LazyOutboundConfigurationObject(this,
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
                                            flow = bean.flow
                                        }
                                )
                            }
                        )
                    })

                streamSettings = StreamSettingsObject().apply {
                    network = "tcp"
                    security = "xtls"
                    xtlsSettings = XTLSObject().apply {
                        if (bean.sni.isNotBlank()) {
                            serverName = bean.sni
                        }
                        if (bean.alpn.isNotBlank()) {
                            alpn = bean.alpn.split(",")
                        }
                    }
                }
            } else if (bean is TrojanBean) {
                protocol = "trojan"
                settings = LazyOutboundConfigurationObject(this,
                    TrojanOutboundConfigurationObject().apply {
                        servers = listOf(
                            TrojanOutboundConfigurationObject.ServerObject().apply {
                                address = bean.serverAddress
                                port = bean.serverPort
                                password = bean.password
                                flow = bean.flow
                                level = 8
                            }
                        )
                    }
                )
                streamSettings = StreamSettingsObject().apply {
                    network = "tcp"
                    security = "xtls"
                    xtlsSettings = XTLSObject().apply {
                        if (bean.sni.isNotBlank()) {
                            serverName = bean.sni
                        }
                        if (bean.alpn.isNotBlank()) {
                            alpn = bean.alpn.split(",")
                        }
                    }
                }
            }
            if (index == 0 && proxy.needXrayMux() && DataStore.enableMux) {
                mux = OutboundObject.MuxObject().apply {
                    enabled = true
                    concurrency = DataStore.muxConcurrency
                }
            }
        }

        if (chain) {
            outbounds.add(
                OutboundObject().apply {
                    protocol = "socks"
                    settings = LazyOutboundConfigurationObject(this,
                        SocksOutboundConfigurationObject().apply {
                            servers = listOf(
                                SocksOutboundConfigurationObject.ServerObject().apply {
                                    address = "127.0.0.1"
                                    port = localPort + 1
                                }
                            )
                        })
                    tag = "front"
                }
            )

            outbound.proxySettings = OutboundObject.ProxySettingsObject().apply {
                tag = "front"
                transportLayer = true
            }
        }

        outbounds.add(outbound)

    }
}
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
import cn.hutool.core.lang.Validator
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.chain.ChainBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.*
import io.nekohasekai.sagernet.ktx.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.*
import kotlin.collections.HashMap

const val TAG_SOCKS = "in"
const val TAG_HTTP = "http"
const val TAG_AGENT = "out"
const val TAG_DIRECT = "bypass"
const val TAG_BLOCK = "block"

const val TAG_DNS_IN = "dns-in"
const val TAG_DNS_OUT = "dns-out"

class V2rayBuildResult(
    var config: V2RayConfig,
    var index: HashMap<Int, ProxyEntity>,
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

    val bind = if (DataStore.allowAccess) "0.0.0.0" else "127.0.0.1"
    val remoteDns = DataStore.remoteDNS.split(",")
    val domesticDns = DataStore.domesticDns.split(',')
    val enableLocalDNS = DataStore.enableLocalDNS
    val routeChina = DataStore.routeChina
    val trafficSniffing = DataStore.trafficSniffing
    val indexMap = hashMapOf<Int, ProxyEntity>()
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

            if (DataStore.blockAds) {
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_BLOCK
                    domain = listOf("geosite:category-ads-all")
                })
            }

            rules.add(RoutingObject.RuleObject().apply {
                type = "field"
                outboundTag = TAG_AGENT
                domain = listOf("domain:googleapis.cn")
            })

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
        }

        var pastExternal = false
        lateinit var pastOutbound: OutboundObject

        proxies.forEachIndexed { index, proxyEntity ->
            Logs.d("Index $index, proxyEntity: ")
            Logs.d(formatObject(proxyEntity))

            val bean = proxyEntity.requireBean()
            indexMap[index] = proxyEntity
            val localPort = socksPort + 10 + index
            val outbound = OutboundObject()

            if (proxyEntity.needExternal()) {
                if (!pastExternal) {
                    outbound.apply {
                        protocol = "socks"
                        settings = LazyOutboundConfigurationObject(
                            SocksOutboundConfigurationObject().apply {
                                servers = listOf(
                                    SocksOutboundConfigurationObject.ServerObject().apply {
                                        address = "127.0.0.1"
                                        port = localPort
                                    }
                                )
                            })
                        tag = if (index == 0) TAG_AGENT else "${proxyEntity.id}"
                        if (index > 0) {
                            pastOutbound.proxySettings =
                                OutboundObject.ProxySettingsObject().apply {
                                    tag = "${proxyEntity.id}"
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
                                            bean.quicSecurity.takeIf { it.isNotBlank() } ?: "none"
                                        key = bean.quicKey
                                        header = QuicObject.HeaderObject().apply {
                                            type =
                                                bean.headerType.takeIf { it.isNotBlank() } ?: "none"
                                        }
                                    }
                                }
                                "grpc" -> {
                                    grpcSettings = GrpcObject().apply {
                                        serviceName = bean.grpcServiceName
                                    }
                                }
                            }

                        }
                    } else if (bean is ShadowsocksBean) {
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
                    }
                    if (index == 0 && DataStore.enableMux && !proxyEntity.needExternal()) {
                        mux = OutboundObject.MuxObject().apply {
                            enabled = true
                            concurrency = DataStore.muxConcurrency
                        }
                    }
                    tag = if (index == 0) TAG_AGENT else "${proxyEntity.id}"
                    if (pastExternal) {
                        inbounds.add(InboundObject().apply {
                            tag = "${proxyEntity.id}-in"
                            listen = "127.0.0.1"
                            port = localPort
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
                        })
                        routing.rules.add(RoutingObject.RuleObject().apply {
                            type = "field"
                            inboundTag = listOf("${proxyEntity.id}-in")
                            outboundTag = "${proxyEntity.id}"
                        })
                    } else if (index > 0) {
                        pastOutbound.proxySettings =
                            OutboundObject.ProxySettingsObject().apply {
                                tag = "${proxyEntity.id}"
                                transportLayer = true
                            }
                    }
                }

                pastExternal = false
                pastOutbound = outbound
                outbounds.add(outbound)
            }
        }

        /*   if (proxies.size > 1) {
               val outNode = proxies.last()
               if (!outNode.needExternal()) {
                   routing.rules.add(RoutingObject.RuleObject().apply {
                       type = "field"
                       inboundTag = listOf("${outNode.id}-in")
                       outboundTag = TAG_DIRECT
                   })
               }
           }*/

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

                settings = LazyOutboundConfigurationObject(
                    BlackholeOutboundConfigurationObject().apply {
                        response =
                            BlackholeOutboundConfigurationObject.ResponseObject().apply {
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

    }.let {
        V2rayBuildResult(
            it,
            indexMap,
            requireWs
        )
    }

}

fun buildXrayConfig(proxy: ProxyEntity, localPort: Int, chain: Boolean): V2RayConfig {

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

        outbounds = mutableListOf()
        val outbound = OutboundObject().apply {
            tag = TAG_AGENT
            if (bean is StandardV2RayBean) {
                bean as VLESSBean
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
                                            flow = bean.flow
                                        }
                                )
                            }
                        )
                    })

                streamSettings = StreamSettingsObject().apply {
                    network = bean.type
                    if (bean.security.isNotBlank()) {
                        security = bean.security
                    }
                    when (security) {
                        "tls" -> {
                            tlsSettings = TLSObject().apply {
                                if (bean.sni.isNotBlank()) {
                                    serverName = bean.sni
                                }
                                if (bean.alpn.isNotBlank()) {
                                    alpn = bean.alpn.split(",")
                                }
                            }
                        }
                        "xtls" -> {
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
                                if (bean.mKcpSeed.isNotBlank()) {
                                    seed = bean.mKcpSeed
                                }
                            }
                        }
                    }

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
                                flow = bean.flow
                                level = 8
                            }
                        )
                    }
                )
                streamSettings = StreamSettingsObject().apply {
                    network = "tcp"
                    security = bean.security
                    when (security) {
                        "tls" -> {
                            tlsSettings = TLSObject().apply {
                                if (bean.sni.isNotBlank()) {
                                    serverName = bean.sni
                                }
                                if (bean.alpn.isNotBlank()) {
                                    alpn = bean.alpn.split(",")
                                }
                            }
                        }
                        "xtls" -> {
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
                }
            }
            if (DataStore.enableMux) {
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
                    settings = LazyOutboundConfigurationObject(
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

fun parseV2Ray(link: String): StandardV2RayBean {
    if (!link.contains("@")) {
        return parseV2RayN(link)
    }

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
                    bean.sni = it
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

        val protocol = url.queryParameter("type") ?: "tcp"
        bean.type = protocol

        when (url.queryParameter("security")) {
            "tls" -> {
                bean.security = "tls"
                url.queryParameter("sni")?.let {
                    bean.sni = it
                }
                url.queryParameter("alpn")?.let {
                    bean.alpn = it
                }
            }
            "xtls" -> {
                bean.security = "xtls"
                url.queryParameter("sni")?.let {
                    bean.sni = it
                }
                url.queryParameter("alpn")?.let {
                    bean.alpn = it
                }
                url.queryParameter("flow")?.let {
                    bean.flow = it
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
            "http" -> {
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let {
                    bean.path = it
                }
            }
            "ws" -> {
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let { pathFakeUrl ->
                    var path = pathFakeUrl
                    if (!path.startsWith("/")) path = "/$path"
                    val pathUrl = "http://localhost$path".toHttpUrlOrNull()
                    if (pathUrl != null) {
                        pathUrl.queryParameter("ed")?.let {
                            bean.wsMaxEarlyData = it.toInt()
                        }

                        path = pathUrl.encodedPath
                    }
                    bean.path = path
                }
                url.queryParameter("ed")?.let {
                    bean.wsMaxEarlyData = it.toInt()
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

    Logs.d(formatObject(bean))

    return bean
}

fun parseV2RayN(link: String): VMessBean {
    val result = link.substringAfter("vmess://").decodeBase64UrlSafe()
    if (result.contains("= vmess")) {
        return parseCsvVMess(result)
    }
    val bean = VMessBean()
    val json = JSONObject(result)

    bean.serverAddress = json.getStr("add") ?: ""
    bean.serverPort = json.getInt("port") ?: 1080
    bean.security = json.getStr("scy") ?: ""
    bean.uuid = json.getStr("id") ?: ""
    bean.alterId = json.getInt("aid") ?: 0
    bean.type = json.getStr("net") ?: ""
    bean.headerType = json.getStr("type") ?: ""
    bean.host = json.getStr("host") ?: ""
    bean.path = json.getStr("path") ?: ""
    bean.name = json.getStr("ps") ?: ""
    bean.sni = json.getStr("sni") ?: ""
    bean.security = if (!json.getStr("tls").isNullOrBlank()) "tls" else ""

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
        it["tls"] = if (security == "tls") "tls" else ""
        it["sni"] = sni
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
        .addQueryParameter("encryption", encryption)

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
                        builder.encodedPath(path.pathSafe())
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
                    builder.encodedPath(path.pathSafe())
                }
            }
            if (type == "ws") {
                if (wsMaxEarlyData > 0) {
                    builder.addQueryParameter("ed", "$wsMaxEarlyData")
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

    if (security.isNotBlank() && security != "none") {
        builder.addQueryParameter("security", security)
        when (security) {
            "tls" -> {
                if (sni.isNotBlank()) {
                    builder.addQueryParameter("sni", sni)
                }
                if (alpn.isNotBlank()) {
                    builder.addQueryParameter("alpn", alpn)
                }
            }
            "xtls" -> {
                if (sni.isNotBlank()) {
                    builder.addQueryParameter("sni", sni)
                }
                if (alpn.isNotBlank()) {
                    builder.addQueryParameter("alpn", alpn)
                }
                if (flow.isNotBlank()) {
                    builder.addQueryParameter("flow", flow)
                }
            }
        }
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toString()
        .replace("https://", if (this is VMessBean) "vmess://" else "vless://")
}
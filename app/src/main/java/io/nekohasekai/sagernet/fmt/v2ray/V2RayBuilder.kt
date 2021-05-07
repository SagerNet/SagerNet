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
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.formatObject
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

        dns = V2RayConfig.DnsObject().apply {
            hosts = mapOf(
                "domain:googleapis.cn" to "googleapis.com"
            )
            servers = mutableListOf()

            servers.addAll(remoteDns.map {
                V2RayConfig.DnsObject.StringOrServerObject().apply {
                    valueX = it
                }
            })

            if (routeChina == 1) {
                servers.add(V2RayConfig.DnsObject.StringOrServerObject().apply {
                    valueY = V2RayConfig.DnsObject.ServerObject().apply {
                        address = domesticDns.first()
                        port = 53
                        domains = listOf("geosite:cn")
                        expectIPs = listOf("geoip:cn")
                    }
                })
            }
        }

        log = V2RayConfig.LogObject().apply {
            loglevel = if (BuildConfig.DEBUG) "debug" else "warning"
        }

        policy = V2RayConfig.PolicyObject().apply {
            levels = mapOf("8" to V2RayConfig.PolicyObject.LevelPolicyObject().apply {
                connIdle = 300
                downlinkOnly = 1
                handshake = 4
                uplinkOnly = 1
            })
            system = V2RayConfig.PolicyObject.SystemPolicyObject().apply {
                statsOutboundDownlink = true
                statsOutboundUplink = true
            }
        }

        inbounds = mutableListOf()
        inbounds.add(
            V2RayConfig.InboundObject().apply {
                tag = TAG_SOCKS
                listen = bind
                port = DataStore.socksPort
                protocol = "socks"
                settings = LazyInboundConfigurationObject(
                    V2RayConfig.SocksInboundConfigurationObject().apply {
                        auth = "noauth"
                        udp = true
                        userLevel = 8
                    })
                if (trafficSniffing) {
                    sniffing = V2RayConfig.InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = listOf("http", "tls")
                        metadataOnly = false
                    }
                }
            }
        )

        val requireHttp = DataStore.requireHttp

        if (requireHttp) {
            inbounds.add(V2RayConfig.InboundObject().apply {
                tag = TAG_HTTP
                listen = bind
                port = DataStore.httpPort
                protocol = "http"
                settings = LazyInboundConfigurationObject(
                    V2RayConfig.HTTPInboundConfigurationObject().apply {
                        allowTransparent = true
                        userLevel = 8
                    })
                if (trafficSniffing) {
                    sniffing = V2RayConfig.InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = listOf("http", "tls")
                        metadataOnly = false
                    }
                }
            })
        }

        outbounds = mutableListOf()

        val socksPort = DataStore.socksPort

        routing = V2RayConfig.RoutingObject().apply {
            domainStrategy = DataStore.domainStrategy
            domainMatcher = DataStore.domainMatcher

            rules = mutableListOf()

            val wsRules = HashMap<String, V2RayConfig.RoutingObject.RuleObject>()

            for (proxyEntity in proxies) {
                val bean = proxyEntity.requireBean()

                if (bean is StandardV2RayBean && bean.type == "ws" && bean.wsUseBrowserForwarder == true) {
                    val route = V2RayConfig.RoutingObject.RuleObject().apply {
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
                rules.add(V2RayConfig.RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_DIRECT
                    ip = listOf("geoip:private")
                })
            }

            if (DataStore.blockAds) {
                rules.add(V2RayConfig.RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_BLOCK
                    domain = listOf("geosite:category-ads-all")
                })
            }

            rules.add(V2RayConfig.RoutingObject.RuleObject().apply {
                type = "field"
                outboundTag = TAG_AGENT
                domain = listOf("domain:googleapis.cn")
            })

            if (routeChina > 0) {
                rules.add(V2RayConfig.RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = if (routeChina == 1) TAG_DIRECT else TAG_BLOCK
                    ip = listOf("geoip:cn")
                })
                rules.add(V2RayConfig.RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = if (routeChina == 1) TAG_DIRECT else TAG_BLOCK
                    domain = listOf("geosite:cn")
                })
            }
        }

        var pastExternal = false
        lateinit var pastOutbound: V2RayConfig.OutboundObject

        proxies.forEachIndexed { index, proxyEntity ->
            Logs.d("Index $index, proxyEntity: ")
            Logs.d(formatObject(proxyEntity))

            val bean = proxyEntity.requireBean()
            indexMap[index] = proxyEntity
            val localPort = socksPort + 10 + index
            val outbound = V2RayConfig.OutboundObject()

            if (proxyEntity.needExternal()) {
                if (!pastExternal) {
                    outbound.apply {
                        protocol = "socks"
                        settings = LazyOutboundConfigurationObject(
                            V2RayConfig.SocksOutboundConfigurationObject().apply {
                                servers = listOf(
                                    V2RayConfig.SocksOutboundConfigurationObject.ServerObject()
                                        .apply {
                                            address = "127.0.0.1"
                                            port = localPort
                                        }
                                )
                            })
                        tag = if (index == 0) TAG_AGENT else "${proxyEntity.id}"
                        if (index > 0) {
                            pastOutbound.proxySettings =
                                V2RayConfig.OutboundObject.ProxySettingsObject().apply {
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
                    val keepAliveInterval = DataStore.tcpKeepAliveInterval
                    val needKeepAliveInterval = keepAliveInterval !in intArrayOf(0, 15)

                    if (bean is SOCKSBean) {
                        protocol = "socks"
                        settings = LazyOutboundConfigurationObject(
                            V2RayConfig.SocksOutboundConfigurationObject().apply {
                                servers = listOf(
                                    V2RayConfig.SocksOutboundConfigurationObject.ServerObject()
                                        .apply {
                                            address = bean.serverAddress
                                            port = bean.serverPort
                                            if (!bean.username.isNullOrBlank()) {
                                                users =
                                                    listOf(V2RayConfig.SocksOutboundConfigurationObject.ServerObject.UserObject()
                                                        .apply {
                                                            user = bean.username
                                                            pass = bean.password
                                                        })
                                            }
                                        }
                                )
                            })
                        if (bean.tls || needKeepAliveInterval) {
                            streamSettings = V2RayConfig.StreamSettingsObject().apply {
                                network = "tcp"
                                if (bean.tls) {
                                    security = "tls"
                                    if (bean.sni.isNotBlank()) {
                                        tlsSettings = V2RayConfig.TLSObject().apply {
                                            serverName = bean.sni
                                        }
                                    }
                                }
                                if (needKeepAliveInterval) {
                                    sockopt =
                                        V2RayConfig.StreamSettingsObject.SockoptObject().apply {
                                            tcpKeepAliveInterval = keepAliveInterval
                                        }
                                }
                            }
                        }
                    } else if (bean is HttpBean) {
                        protocol = "http"
                        settings = LazyOutboundConfigurationObject(
                            V2RayConfig.HTTPOutboundConfigurationObject().apply {
                                servers = listOf(
                                    V2RayConfig.HTTPOutboundConfigurationObject.ServerObject()
                                        .apply {
                                            address = bean.serverAddress
                                            port = bean.serverPort
                                            if (!bean.username.isNullOrBlank()) {
                                                users =
                                                    listOf(V2RayConfig.HTTPInboundConfigurationObject.AccountObject()
                                                        .apply {
                                                            user = bean.username
                                                            pass = bean.password
                                                        })
                                            }
                                        }
                                )
                            })
                        if (bean.tls || needKeepAliveInterval) {
                            streamSettings = V2RayConfig.StreamSettingsObject().apply {
                                network = "tcp"
                                if (bean.tls) {
                                    security = "tls"
                                    if (bean.sni.isNotBlank()) {
                                        tlsSettings = V2RayConfig.TLSObject().apply {
                                            serverName = bean.sni
                                        }
                                    }
                                }
                                if (needKeepAliveInterval) {
                                    sockopt =
                                        V2RayConfig.StreamSettingsObject.SockoptObject().apply {
                                            tcpKeepAliveInterval = keepAliveInterval
                                        }
                                }
                            }
                        }
                    } else if (bean is StandardV2RayBean) {
                        if (bean is VMessBean) {
                            protocol = "vmess"
                            settings = LazyOutboundConfigurationObject(
                                V2RayConfig.VMessOutboundConfigurationObject().apply {
                                    vnext = listOf(
                                        V2RayConfig.VMessOutboundConfigurationObject.ServerObject()
                                            .apply {
                                                address = bean.serverAddress
                                                port = bean.serverPort
                                                users = listOf(
                                                    V2RayConfig.VMessOutboundConfigurationObject.ServerObject.UserObject()
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
                                V2RayConfig.VLESSOutboundConfigurationObject().apply {
                                    vnext = listOf(
                                        V2RayConfig.VLESSOutboundConfigurationObject.ServerObject()
                                            .apply {
                                                address = bean.serverAddress
                                                port = bean.serverPort
                                                users = listOf(
                                                    V2RayConfig.VLESSOutboundConfigurationObject.ServerObject.UserObject()
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

                        streamSettings = V2RayConfig.StreamSettingsObject().apply {
                            network = bean.type
                            if (bean.security.isNotBlank()) {
                                security = bean.security
                            }
                            if (security == "tls") {
                                tlsSettings = V2RayConfig.TLSObject().apply {
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
                                    tcpSettings = V2RayConfig.TcpObject().apply {
                                        if (bean.headerType == "http") {
                                            header = V2RayConfig.TcpObject.HeaderObject().apply {
                                                type = "http"
                                                if (bean.host.isNotBlank() || bean.path.isNotBlank()) {
                                                    request =
                                                        V2RayConfig.TcpObject.HeaderObject.HTTPRequestObject()
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
                                    kcpSettings = V2RayConfig.KcpObject().apply {
                                        mtu = 1350
                                        tti = 50
                                        uplinkCapacity = 12
                                        downlinkCapacity = 100
                                        congestion = false
                                        readBufferSize = 1
                                        writeBufferSize = 1
                                        header = V2RayConfig.KcpObject.HeaderObject().apply {
                                            type = bean.headerType
                                        }
                                        if (bean.mKcpSeed.isNotBlank()) {
                                            seed = bean.mKcpSeed
                                        }
                                    }
                                }
                                "ws" -> {
                                    wsSettings = V2RayConfig.WebSocketObject().apply {
                                        headers = mutableMapOf()

                                        if (bean.host.isNotBlank()) {
                                            headers["Host"] = bean.host
                                        }

                                        path = bean.path.takeIf { it.isNotBlank() } ?: "/"

                                        if (bean.wsMaxEarlyData > 0) {
                                            maxEarlyData = bean.wsMaxEarlyData

                                            val pathUrl = "http://localhost$path".toHttpUrlOrNull()
                                            if (pathUrl != null) {
                                                pathUrl.queryParameter("ed")?.let {
                                                    path = pathUrl.newBuilder()
                                                        .removeAllQueryParameters("ed")
                                                        .build()
                                                        .toString()
                                                        .substringAfter("http://localhost")
                                                    earlyDataHeaderName = "Sec-WebSocket-Protocol"
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

                                    httpSettings = V2RayConfig.HttpObject().apply {
                                        if (bean.host.isNotBlank()) {
                                            host = bean.host.split(",")
                                        }

                                        path = bean.path.takeIf { it.isNotBlank() } ?: "/"
                                    }
                                }
                                "quic" -> {
                                    quicSettings = V2RayConfig.QuicObject().apply {
                                        security =
                                            bean.quicSecurity.takeIf { it.isNotBlank() } ?: "none"
                                        key = bean.quicKey
                                        header = V2RayConfig.QuicObject.HeaderObject().apply {
                                            type =
                                                bean.headerType.takeIf { it.isNotBlank() } ?: "none"
                                        }
                                    }
                                }
                                "grpc" -> {
                                    grpcSettings = V2RayConfig.GrpcObject().apply {
                                        serviceName = bean.grpcServiceName
                                    }
                                }
                            }

                            if (needKeepAliveInterval) {
                                sockopt = V2RayConfig.StreamSettingsObject.SockoptObject().apply {
                                    tcpKeepAliveInterval = keepAliveInterval
                                }
                            }

                        }
                    } else if (bean is ShadowsocksBean) {
                        protocol = "shadowsocks"
                        settings = LazyOutboundConfigurationObject(
                            V2RayConfig.ShadowsocksOutboundConfigurationObject().apply {
                                servers = listOf(
                                    V2RayConfig.ShadowsocksOutboundConfigurationObject.ServerObject()
                                        .apply {
                                            address = bean.serverAddress
                                            port = bean.serverPort
                                            method = bean.method
                                            password = bean.password
                                        }
                                )
                                if (needKeepAliveInterval) {
                                    streamSettings = V2RayConfig.StreamSettingsObject().apply {
                                        sockopt =
                                            V2RayConfig.StreamSettingsObject.SockoptObject().apply {
                                                tcpKeepAliveInterval = keepAliveInterval
                                            }
                                    }
                                }
                            })
                    } else if (bean is TrojanBean) {
                        protocol = "trojan"
                        settings = LazyOutboundConfigurationObject(
                            V2RayConfig.TrojanOutboundConfigurationObject().apply {
                                servers = listOf(
                                    V2RayConfig.TrojanOutboundConfigurationObject.ServerObject()
                                        .apply {
                                            address = bean.serverAddress
                                            port = bean.serverPort
                                            password = bean.password
                                            level = 8
                                        }
                                )
                            }
                        )
                        streamSettings = V2RayConfig.StreamSettingsObject().apply {
                            network = "tcp"
                            security = "tls"
                            if (bean.sni.isNotBlank()) {
                                tlsSettings = V2RayConfig.TLSObject().apply {
                                    serverName = bean.sni
                                }
                            }
                            if (needKeepAliveInterval) {
                                sockopt = V2RayConfig.StreamSettingsObject.SockoptObject().apply {
                                    tcpKeepAliveInterval = keepAliveInterval
                                }
                            }
                        }
                    }
                    if (index == 0 && proxyEntity.needCoreMux() && DataStore.enableMux) {
                        mux = V2RayConfig.OutboundObject.MuxObject().apply {
                            enabled = true
                            concurrency = DataStore.muxConcurrency
                        }
                    }
                    tag = if (index == 0) TAG_AGENT else "${proxyEntity.id}"
                    if (pastExternal) {
                        inbounds.add(V2RayConfig.InboundObject().apply {
                            tag = "${proxyEntity.id}-in"
                            listen = "127.0.0.1"
                            port = localPort
                            protocol = "socks"
                            settings = LazyInboundConfigurationObject(
                                V2RayConfig.SocksInboundConfigurationObject().apply {
                                    auth = "noauth"
                                    udp = true
                                    userLevel = 8
                                })
                            if (trafficSniffing) {
                                sniffing = V2RayConfig.InboundObject.SniffingObject().apply {
                                    enabled = true
                                    destOverride = listOf("http", "tls")
                                    metadataOnly = false
                                }
                            }
                        })
                        routing.rules.add(V2RayConfig.RoutingObject.RuleObject().apply {
                            type = "field"
                            inboundTag = listOf("${proxyEntity.id}-in")
                            outboundTag = "${proxyEntity.id}"
                        })
                    } else if (index > 0) {
                        pastOutbound.proxySettings =
                            V2RayConfig.OutboundObject.ProxySettingsObject().apply {
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
            browserForwarder = V2RayConfig.BrowserForwarderObject().apply {
                listenAddr = "127.0.0.1"
                listenPort = DataStore.socksPort + 1
            }
        }

        outbounds.add(
            V2RayConfig.OutboundObject().apply {
                tag = TAG_DIRECT
                protocol = "freedom"
            }
        )

        outbounds.add(
            V2RayConfig.OutboundObject().apply {
                tag = TAG_BLOCK
                protocol = "blackhole"

                settings = LazyOutboundConfigurationObject(
                    V2RayConfig.BlackholeOutboundConfigurationObject().apply {
                        response =
                            V2RayConfig.BlackholeOutboundConfigurationObject.ResponseObject()
                                .apply {
                                    type = "http"
                                }
                    }
                )
            }
        )

        if (enableLocalDNS) {
            inbounds.add(
                V2RayConfig.InboundObject().apply {
                    tag = TAG_DNS_IN
                    listen = "127.0.0.1"
                    port = DataStore.localDNSPort
                    protocol = "dokodemo-door"
                    settings = LazyInboundConfigurationObject(
                        V2RayConfig.DokodemoDoorInboundConfigurationObject().apply {
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
                V2RayConfig.OutboundObject().apply {
                    protocol = "dns"
                    tag = TAG_DNS_OUT
                    settings = LazyOutboundConfigurationObject(
                        V2RayConfig.DNSOutboundConfigurationObject().apply {
                            network = "tcp"
                        }
                    )
                    proxySettings
                }
            )

            if (!domesticDns.first().startsWith("https")) {
                routing.rules.add(0, V2RayConfig.RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_DIRECT
                    ip = listOf(domesticDns.first())
                    port = "53"
                })
            }
            if (!remoteDns.first().startsWith("https")) {
                routing.rules.add(0, V2RayConfig.RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_AGENT
                    ip = listOf(remoteDns.first())
                    port = "53"
                })
            }

            routing.rules.add(0, V2RayConfig.RoutingObject.RuleObject().apply {
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

        dns = V2RayConfig.DnsObject().apply {
            servers = mutableListOf()

            if (!DataStore.enableLocalDNS) {
                servers.addAll(remoteDns.map {
                    V2RayConfig.DnsObject.StringOrServerObject().apply {
                        valueX = it
                    }
                })
            } else {
                servers.add(
                    V2RayConfig.DnsObject.StringOrServerObject().apply {
                        valueY = V2RayConfig.DnsObject.ServerObject().apply {
                            address = "127.0.0.1"
                            port = DataStore.localDNSPort
                        }
                    }
                )
            }
        }

        log = V2RayConfig.LogObject().apply {
            loglevel = if (BuildConfig.DEBUG) "debug" else "warning"
        }

        policy = V2RayConfig.PolicyObject().apply {
            levels = mapOf("8" to V2RayConfig.PolicyObject.LevelPolicyObject().apply {
                connIdle = 300
                downlinkOnly = 1
                handshake = 4
                uplinkOnly = 1
            })
            system = V2RayConfig.PolicyObject.SystemPolicyObject().apply {
                statsOutboundDownlink = true
                statsOutboundUplink = true
            }
        }

        inbounds = mutableListOf()
        inbounds.add(
            V2RayConfig.InboundObject().apply {
                tag = TAG_SOCKS
                listen = "127.0.0.1"
                port = localPort
                protocol = "socks"
                settings = LazyInboundConfigurationObject(
                    V2RayConfig.SocksInboundConfigurationObject().apply {
                        auth = "noauth"
                        udp = true
                        userLevel = 8
                    })
                if (trafficSniffing) {
                    sniffing = V2RayConfig.InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = listOf("http", "tls")
                        metadataOnly = false
                    }
                }
            }
        )

        outbounds = mutableListOf()
        val outbound = V2RayConfig.OutboundObject().apply {
            tag = TAG_AGENT
            if (bean is StandardV2RayBean) {
                bean as VLESSBean
                protocol = "vless"
                settings = LazyOutboundConfigurationObject(
                    V2RayConfig.VLESSOutboundConfigurationObject().apply {
                        vnext = listOf(
                            V2RayConfig.VLESSOutboundConfigurationObject.ServerObject().apply {
                                address = bean.serverAddress
                                port = bean.serverPort
                                users = listOf(
                                    V2RayConfig.VLESSOutboundConfigurationObject.ServerObject.UserObject()
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

                streamSettings = V2RayConfig.StreamSettingsObject().apply {
                    network = "tcp"
                    security = "xtls"
                    xtlsSettings = V2RayConfig.XTLSObject().apply {
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
                settings = LazyOutboundConfigurationObject(
                    V2RayConfig.TrojanOutboundConfigurationObject().apply {
                        servers = listOf(
                            V2RayConfig.TrojanOutboundConfigurationObject.ServerObject().apply {
                                address = bean.serverAddress
                                port = bean.serverPort
                                password = bean.password
                                flow = bean.flow
                                level = 8
                            }
                        )
                    }
                )
                streamSettings = V2RayConfig.StreamSettingsObject().apply {
                    network = "tcp"
                    security = "xtls"
                    xtlsSettings = V2RayConfig.XTLSObject().apply {
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
                mux = V2RayConfig.OutboundObject.MuxObject().apply {
                    enabled = true
                    concurrency = DataStore.muxConcurrency
                }
            }
        }

        if (chain) {
            outbounds.add(
                V2RayConfig.OutboundObject().apply {
                    protocol = "socks"
                    settings = LazyOutboundConfigurationObject(
                        V2RayConfig.SocksOutboundConfigurationObject().apply {
                            servers = listOf(
                                V2RayConfig.SocksOutboundConfigurationObject.ServerObject().apply {
                                    address = "127.0.0.1"
                                    port = localPort + 1
                                }
                            )
                        })
                    tag = "front"
                }
            )
            outbound.proxySettings = V2RayConfig.OutboundObject.ProxySettingsObject().apply {
                tag = "front"
                transportLayer = true
            }
        }

        outbounds.add(outbound)

    }
}
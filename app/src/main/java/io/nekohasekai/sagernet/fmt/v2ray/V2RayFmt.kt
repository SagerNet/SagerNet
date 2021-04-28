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
                } else if (bean is AbstractV2RayBean) {
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
                        network = bean.network
                        security = if (bean.tls) "tls" else ""
                        if (bean.tls) {
                            tlsSettings = TLSObject().apply {
                                if (bean.sni.isNotBlank()) {
                                    serverName = bean.sni
                                }
                            }
                        }

                        when (network) {
                            "tcp" -> {
                                tcpSettings = TcpObject().apply {
                                    if (bean.headerType == "http") {
                                        header = TcpObject.HeaderObject().apply {
                                            type = "http"
                                            if (bean.requestHost.isNotBlank() || bean.path.isNotBlank()) {
                                                request = TcpObject.HeaderObject.HTTPRequestObject()
                                                    .apply {
                                                        headers = mutableMapOf()
                                                        if (bean.requestHost.isNotBlank()) {
                                                            headers["Host"] =
                                                                bean.requestHost.split(",")
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

                                    if (bean.requestHost.isNotBlank()) {
                                        headers["Host"] = bean.requestHost
                                    }

                                    path = bean.path.takeIf { it.isNotBlank() } ?: "/"

                                    val wsMaxEarlyData = DataStore.wsMaxEarlyData
                                    if (wsMaxEarlyData != 0) {
                                        maxEarlyData = wsMaxEarlyData
                                    }

                                    if (DataStore.wsBrowserForwarding) {
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
                                    if (bean.requestHost.isNotBlank()) {
                                        host = bean.requestHost.split(",")
                                    }

                                    path = bean.path.takeIf { it.isNotBlank() } ?: "/"
                                }
                            }
                            "quic" -> {
                                quicSettings = QuicObject().apply {
                                    security = bean.requestHost.takeIf { it.isNotBlank() } ?: "none"
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

fun parseVmess(link: String): VMessBean {
    if (link.contains("?") || link.startsWith("vmess1://")) return parseVmess1(link)
    val result = Base64.decodeStr(link.substringAfter("vmess://"))
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
    bean.network = json.getStr("net")
    bean.headerType = json.getStr("type")
    bean.requestHost = json.getStr("host")
    bean.path = json.getStr("path")
    bean.name = json.getStr("ps")
    bean.sni = json.getStr("sni")
    bean.tls = !json.getStr("tls").isNullOrBlank()

    if (json.getInt("v", 2) < 2) {
        when (bean.network) {
            "ws" -> {
                var path = ""
                var host = ""
                val lstParameter = bean.requestHost.split(";")
                if (lstParameter.isNotEmpty()) {
                    path = lstParameter[0].trim()
                }
                if (lstParameter.size > 1) {
                    path = lstParameter[0].trim()
                    host = lstParameter[1].trim()
                }
                bean.path = path
                bean.requestHost = host
            }
            "h2" -> {
                var path = ""
                var host = ""
                val lstParameter = bean.requestHost.split(";")
                if (lstParameter.isNotEmpty()) {
                    path = lstParameter[0].trim()
                }
                if (lstParameter.size > 1) {
                    path = lstParameter[0].trim()
                    host = lstParameter[1].trim()
                }
                bean.path = path
                bean.requestHost = host
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
            it == "over-tls=true" -> bean.tls = true
            it.startsWith("tls-host=") -> bean.requestHost = it.substringAfter("=")
            it.startsWith("obfs=") -> bean.network = it.substringAfter("=")

            it.startsWith("obfs-path=") || it.contains("Host:") -> {

                runCatching {

                    bean.path = it
                        .substringAfter("obfs-path=\"")
                        .substringBefore("\"obfs")

                }

                runCatching {

                    bean.requestHost = it
                        .substringAfter("Host:")
                        .substringBefore("[")

                }

            }

        }

    }

    return bean

}


fun parseVmess1(link: String): VMessBean {
    val bean = VMessBean()
    val lnk = link
        .replace("vmess://", "https://")
        .replace("vmess1://", "https://")
        .toHttpUrl()
    bean.serverAddress = lnk.host
    bean.serverPort = lnk.port
    bean.uuid = lnk.username
    bean.name = lnk.fragment
    lnk.queryParameterNames.forEach {
        when (it) {
            //  "tag" -> bean.tag = lnk.queryParameter(it)
            "tls" -> bean.tls = lnk.queryParameter(it) == "true"
            "network" -> {
                bean.network = lnk.queryParameter(it)!!
                if (bean.network in arrayOf("http", "ws")) {
                    bean.path = lnk.pathSegments.joinToString("/", "/")
                }
            }
            /*  "kcp.uplinkcapacity" -> bean.kcpUpLinkCapacity = lnk.queryParameter(it)!!.toInt()
              "kcp.downlinkcapacity" -> bean.kcpDownLinkCapacity =
                  lnk.queryParameter(it)!!.toInt()*/
            "header" -> bean.headerType = lnk.queryParameter(it)
            // custom
            "host" -> bean.requestHost = lnk.queryParameter(it)
            "sni" -> bean.sni = lnk.queryParameter(it)
            "security" -> bean.security = lnk.queryParameter(it)
            "alterid" -> bean.alterId = lnk.queryParameter(it)!!.toInt()
        }
    }

    bean.initDefaultValues()
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
        it["net"] = network
        it["host"] = requestHost
        it["type"] = headerType
        it["path"] = path
        it["tls"] = if (tls) "true" else ""
        it["sni"] = sni
        it["scy"] = security

    }.toString().let { Base64.encodeUrlSafe(it) }

}

fun VMessBean.toVmess1(): String {

    val builder = HttpUrl.Builder()
        .scheme("https")
        .host(serverAddress)
        .port(serverPort)

    if (!uuid.isNullOrBlank()) {
        builder.username(uuid)
    }

    if (!path.isNullOrBlank()) {
        builder.addPathSegment(path)
    }

    /* if (!tag.isNullOrBlank()) {
         builder.addQueryParameter("tag", tag)
     }
 */
    if (!network.isNullOrBlank()) {
        builder.addQueryParameter("network", network)
    }

    /* if (kcpUpLinkCapacity != 0) {
         builder.addQueryParameter("kcp.uplinkcapacity", "$kcpUpLinkCapacity")
     }

     if (kcpDownLinkCapacity != 0) {
         builder.addQueryParameter("kcp.downlinkcapacity", "$kcpDownLinkCapacity")
     }*/

    if (!headerType.isNullOrBlank()) {
        builder.addQueryParameter("header", headerType)
    }

    /* if (mux != 0) {
         builder.addQueryParameter("mux", "$mux")
     }*/

    if (!name.isNullOrBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    // custom

    if (!requestHost.isNullOrBlank()) {
        builder.addQueryParameter("host", requestHost)
    }

    if (!sni.isNullOrBlank()) {
        builder.addQueryParameter("sni", sni)
    }

    if (!security.isNullOrBlank()) {
        builder.addQueryParameter("security", security)
    }

    if (alterId != 0) {
        builder.addQueryParameter("alterid", "$alterId")
    }

    return builder.build().toString().replace("https://", "vmess://")

}
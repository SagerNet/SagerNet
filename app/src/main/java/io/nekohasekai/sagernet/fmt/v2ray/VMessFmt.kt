package io.nekohasekai.sagernet.fmt.v2ray

import cn.hutool.core.codec.Base64
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.V2rayConfig.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

fun buildV2rayConfig(proxy: ProxyEntity, listen: String, port: Int): V2rayConfig {

    val bean = proxy.requireBean()

    return V2rayConfig().apply {

        dns = DnsObject().apply {
            servers = listOf(
                DnsObject.StringOrServerObject().apply {
                    valueX = "1.1.1.1"
                }
            )
        }

        log = LogObject().apply {
            loglevel = "debug"
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

        inbounds = listOf(
            InboundObject().apply {
                tag = "in"
                this.listen = listen
                this.port = port
                protocol = "socks"
                settings = LazyInboundConfigurationObject(
                    SocksInboundConfigurationObject().apply {
                        auth = "noauth"
                        udp = bean is SOCKSBean && bean.udp
                        userLevel = 0
                    })
            }
        )

        outbounds = listOf(
            OutboundObject().apply {
                tag = "out"
                if (bean is SOCKSBean) {
                    protocol = "socks"
                    settings = LazyOutboundConfigurationObject(
                        SocksOutboundConfigurationObject().apply {
                            servers = listOf(
                                SocksOutboundConfigurationObject.ServerObject().apply {
                                    address = bean.serverAddress
                                    this.port = bean.serverPort
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
                } else if (bean is ShadowsocksBean) {
                    if (!proxy.useExternalShadowsocks()) {
                        protocol = "shadowsocks"
                        settings = LazyOutboundConfigurationObject(
                            ShadowsocksOutboundConfigurationObject().apply {
                                servers = listOf(
                                    ShadowsocksOutboundConfigurationObject.ServerObject().apply {
                                        address = bean.serverAddress
                                        this.port = bean.serverPort
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
                                        this.port = port + 10
                                    }
                                )
                            })
                    }
                }
            },
            OutboundObject().apply {
                tag = "direct"
                protocol = "freedom"
            }
        )

        routing = RoutingObject().apply {
            domainStrategy = "IPIfNonMatch"
            rules = listOf(RoutingObject.RuleObject().apply {
                inboundTag = listOf(
                    "in"
                )
                outboundTag = "out"
                type = "field"
            })
        }

        stats = emptyMap()

    }

}

fun parseVmess(link: String): VMessBean {
    if (link.contains("?") || link.startsWith("vmess1://")) return parseVmess1(link)

    val bean = VMessBean()
    val json = JSONObject(Base64.decodeStr(link.substringAfter("vmess://")))

    bean.serverAddress = json.getStr("add")
    bean.serverPort = json.getInt("port")
    bean.uuid = json.getStr("id")
    bean.alterId = json.getInt("aid")
    bean.network = json.getStr("network")
    bean.header = json.getStr("type")
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

    bean.initDefaultValues()
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
            "tag" -> bean.tag = lnk.queryParameter(it)
            "tls" -> bean.tls = lnk.queryParameter(it) == "true"
            "network" -> {
                bean.network = lnk.queryParameter(it)!!
                if (bean.network in arrayOf("http", "ws")) {
                    bean.path = lnk.pathSegments.joinToString("/", "/")
                }
            }
            "kcp.uplinkcapacity" -> bean.kcpUpLinkCapacity = lnk.queryParameter(it)!!.toInt()
            "kcp.downlinkcapacity" -> bean.kcpDownLinkCapacity = lnk.queryParameter(it)!!.toInt()
            "header" -> bean.header = lnk.queryParameter(it)
            "mux" -> bean.mux = lnk.queryParameter(it)!!.toInt()
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

    if (!tag.isNullOrBlank()) {
        builder.addQueryParameter("tag", tag)
    }

    if (!network.isNullOrBlank()) {
        builder.addQueryParameter("network", network)
    }

    if (kcpUpLinkCapacity != 0) {
        builder.addQueryParameter("kcp.uplinkcapacity", "$kcpUpLinkCapacity")
    }

    if (kcpDownLinkCapacity != 0) {
        builder.addQueryParameter("kcp.downlinkcapacity", "$kcpDownLinkCapacity")
    }

    if (!header.isNullOrBlank()) {
        builder.addQueryParameter("header", header)
    }

    if (mux != 0) {
        builder.addQueryParameter("mux", "$mux")
    }

    if (!name.isNullOrBlank()) {
        builder.fragment(name)
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

    return builder.build().toString().replace("https://", "vmess1://")

}
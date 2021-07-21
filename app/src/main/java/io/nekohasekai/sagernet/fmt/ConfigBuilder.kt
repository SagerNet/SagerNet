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

import android.os.Build
import cn.hutool.core.util.NumberUtil
import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import com.google.gson.JsonSyntaxException
import io.nekohasekai.sagernet.DnsMode
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.V2rayBuildResult.IndexEntity
import io.nekohasekai.sagernet.fmt.brook.BrookBean
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.internal.BalancerBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.*
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.*

const val TAG_SOCKS = "socks"
const val TAG_HTTP = "http"
const val TAG_TRANS = "trans"

const val TAG_AGENT = "proxy"
const val TAG_DIRECT = "bypass"
const val TAG_BLOCK = "block"

const val TAG_DNS_IN = "dns-in"
const val TAG_DNS_OUT = "dns-out"

const val TAG_API_IN = "api-in"
const val TAG_API = "api"

const val LOCALHOST = "127.0.0.1"
const val IP6_LOCALHOST = "::1"

class V2rayBuildResult(
    var config: String,
    var index: List<IndexEntity>,
    var requireWs: Boolean,
    var wsPort: Int,
    var outboundTags: ArrayList<String>,
    var outboundTagsCurrent: ArrayList<String>,
    var outboundTagsAll: HashMap<String, ProxyEntity>,
    var directTag: String,
    var enableApi: Boolean,
    var observatoryTags: MutableSet<String>
) {
    data class IndexEntity(
        var isBalancer: Boolean, var chain: LinkedHashMap<Int, ProxyEntity>
    )
}

fun buildV2RayConfig(
    proxy: ProxyEntity, forTest: Boolean = false, testPort: Int = 0
): V2rayBuildResult {

    val outboundTags = ArrayList<String>()
    val outboundTagsCurrent = ArrayList<String>()
    val outboundTagsAll = HashMap<String, ProxyEntity>()
    val globalOutbounds = ArrayList<String>()

    fun ProxyEntity.resolveChain(): MutableList<ProxyEntity> {
        val bean = requireBean()
        if (bean is ChainBean) {
            val beans = SagerDatabase.proxyDao.getEntities(bean.proxies)
            val beansMap = beans.map { it.id to it }.toMap()
            val beanList = ArrayList<ProxyEntity>()
            for (proxyId in bean.proxies) {
                val item = beansMap[proxyId] ?: continue
                when (item.type) {
                    ProxyEntity.TYPE_BALANCER -> error("Balancer is incompatible with chain")
                    ProxyEntity.TYPE_CONFIG -> error("Custom config is incompatible with chain")
                }
                beanList.addAll(item.resolveChain())
            }
            return beanList.asReversed()
        } else if (bean is BalancerBean) {
            val beans = if (bean.type == BalancerBean.TYPE_LIST) {
                SagerDatabase.proxyDao.getEntities(bean.proxies)
            } else {
                SagerDatabase.proxyDao.getByGroup(bean.groupId)
            }
            val beansMap = beans.map { it.id to it }.toMap()
            val beanList = ArrayList<ProxyEntity>()
            for (proxyId in beansMap.keys) {
                val item = beansMap[proxyId] ?: continue
                if (item.id == id) continue
                when (item.type) {
                    ProxyEntity.TYPE_BALANCER -> error("Nested balancers are not supported")
                    ProxyEntity.TYPE_CHAIN -> error("Chain is incompatible with balancer")
                    ProxyEntity.TYPE_CONFIG -> error("Custom config is incompatible with balancer")
                }
                beanList.add(item)
            }
            return beanList
        }
        return mutableListOf(this)
    }

    val proxies = proxy.resolveChain()
    val extraRules = if (forTest) listOf() else SagerDatabase.rulesDao.enabledRules()
    val extraProxies =
        if (forTest) mapOf() else SagerDatabase.proxyDao.getEntities(extraRules.mapNotNull { rule ->
            rule.outbound.takeIf { it > 0 && it != proxy.id }
        }.toHashSet().toList()).associate {
            (it.id to ((it.type == ProxyEntity.TYPE_BALANCER) to lazy {
                it.balancerBean!!.strategy
            })) to it.resolveChain()
        }

    val bind = if (!forTest && DataStore.allowAccess) "0.0.0.0" else LOCALHOST

    val dnsMode = DataStore.dnsMode
    DataStore.dnsModeFinal = dnsMode
    val systemDns = DataStore.systemDns.split("\n")
    val localDns = DataStore.localDns.split("\n")
    val domesticDns = DataStore.domesticDns.split("\n")
    val enableDomesticDns = DataStore.enableDomesticDns
    val useFakeDns =
        if (forTest) false else dnsMode in arrayOf(DnsMode.FAKEDNS, DnsMode.FAKEDNS_LOCAL)
    val useLocalDns = dnsMode in arrayOf(DnsMode.LOCAL, DnsMode.FAKEDNS_LOCAL)
    val trafficSniffing = DataStore.trafficSniffing
    val indexMap = ArrayList<IndexEntity>()
    var requireWs = false
    val requireHttp =
        forTest || Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || DataStore.requireHttp
    val requireTransproxy = if (forTest) false else DataStore.requireTransproxy

    val ipv6Mode = if (forTest) IPv6Mode.ENABLE else DataStore.ipv6Mode

    return V2RayConfig().apply {

        dns = DnsObject().apply {
            hosts = mapOf(
                "domain:googleapis.cn" to "googleapis.com"
            )
            servers = mutableListOf()

            if (dnsMode == DnsMode.SYSTEM) {
                if (!forTest) DataStore.systemDnsFinal = systemDns.joinToString("\n")
                servers.addAll(systemDns.map {
                    DnsObject.StringOrServerObject().apply {
                        valueX = it
                    }
                })
            } else if (dnsMode == DnsMode.LOCAL || dnsMode == DnsMode.FAKEDNS_LOCAL) {
                servers.addAll(localDns.map {
                    DnsObject.StringOrServerObject().apply {
                        valueX = it
                    }
                })
            } else if (dnsMode == DnsMode.FAKEDNS) {
                servers.add(DnsObject.StringOrServerObject().apply {
                    valueX = "fakedns"
                })
            }

            if (useFakeDns) {
                fakedns = mutableListOf()
                fakedns.add(FakeDnsObject().apply {
                    ipPool = "${VpnService.FAKEDNS_VLAN4_CLIENT}/15"
                    poolSize = 65535
                })
                if (ipv6Mode != IPv6Mode.DISABLE) {
                    fakedns.add(FakeDnsObject().apply {
                        ipPool = "${VpnService.FAKEDNS_VLAN6_CLIENT}/18"
                        poolSize = 65535
                    })
                }
            }

            when (ipv6Mode) {
                IPv6Mode.DISABLE -> {
                    queryStrategy = "UseIPv4"
                }
                IPv6Mode.ONLY -> {
                    queryStrategy = "UseIPv6"
                }
            }
        }

        log = LogObject().apply {
            loglevel = if (DataStore.enableLog) "debug" else "error"
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

        if (!forTest) inbounds.add(InboundObject().apply {
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
            if (trafficSniffing || useFakeDns) {
                sniffing = InboundObject.SniffingObject().apply {
                    enabled = true
                    destOverride = if (useFakeDns) {
                        listOf("fakedns", "http", "tls")
                    } else {
                        listOf("http", "tls")
                    }
                    metadataOnly = false
                }
            }
        })

        if (requireHttp) {
            inbounds.add(InboundObject().apply {
                tag = TAG_HTTP
                listen = bind
                port = if (forTest) testPort else DataStore.httpPort
                protocol = "http"
                settings =
                    LazyInboundConfigurationObject(this, HTTPInboundConfigurationObject().apply {
                        allowTransparent = true
                        userLevel = 8
                    })
                if (trafficSniffing || useFakeDns) {
                    sniffing = InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = if (useFakeDns) {
                            listOf("fakedns", "http", "tls")
                        } else {
                            listOf("http", "tls")
                        }
                        metadataOnly = false
                    }
                }
            })
        }

        if (requireTransproxy) {
            inbounds.add(InboundObject().apply {
                tag = TAG_TRANS
                listen = bind
                port = DataStore.transproxyPort
                protocol = "dokodemo-door"
                settings = LazyInboundConfigurationObject(
                    this,
                    DokodemoDoorInboundConfigurationObject().apply {
                        network = "tcp,udp"
                        followRedirect = true
                        userLevel = 8
                    })
                if (trafficSniffing || useFakeDns) {
                    sniffing = InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = if (useFakeDns) {
                            listOf("fakedns", "http", "tls")
                        } else {
                            listOf("http", "tls")
                        }
                        metadataOnly = false
                    }
                }
                when (DataStore.transproxyMode) {
                    1 -> streamSettings = StreamSettingsObject().apply {
                        sockopt = StreamSettingsObject.SockoptObject().apply {
                            tproxy = "tproxy"
                        }
                    }
                }
            })
        }

        outbounds = mutableListOf()

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
                            bean.host.isIpAddress() -> {
                                ip = listOf(bean.host)
                            }
                            bean.host.isNotBlank() -> {
                                domain = listOf(bean.host)
                            }
                            bean.serverAddress.isIpAddress() -> {
                                ip = listOf(bean.serverAddress)
                            }
                            else -> domain = listOf(bean.serverAddress)
                        }
                    }
                    wsRules[bean.host.takeIf { !it.isNullOrBlank() } ?: bean.serverAddress] = route
                }
            }

            rules.addAll(wsRules.values)

            if (DataStore.bypassLan && DataStore.bypassLanInCoreOnly) {
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_DIRECT
                    ip = listOf("geoip:private")
                })
            }
        }

        fun buildChain(
            tagOutbound: String,
            profileList: List<ProxyEntity>,
            isBalancer: Boolean,
            balancerStrategy: (() -> String)
        ): String {
            var pastExternal = false
            lateinit var pastOutbound: OutboundObject
            lateinit var currentOutbound: OutboundObject
            lateinit var pastInboundTag: String
            val chainMap = LinkedHashMap<Int, ProxyEntity>()
            indexMap.add(IndexEntity(isBalancer, chainMap))
            val chainOutbounds = ArrayList<OutboundObject>()
            var chainOutbound = ""

            profileList.forEachIndexed { index, proxyEntity ->
                Logs.d("Index $index, proxyEntity: ")
                Logs.d(formatObject(proxyEntity))

                val bean = proxyEntity.requireBean()
                currentOutbound = OutboundObject()

                val tagIn: String
                val needGlobal: Boolean

                if (isBalancer || index == profileList.lastIndex && !pastExternal) {
                    tagIn = "$TAG_AGENT-global-${proxyEntity.id}"
                    needGlobal = true
                } else {
                    tagIn = if (index == 0) tagOutbound else {
                        "$tagOutbound-${proxyEntity.id}"
                    }
                    needGlobal = false
                }

                if (index == profileList.lastIndex) {
                    chainOutbound = tagIn
                }

                if (needGlobal) {
                    if (globalOutbounds.contains(tagIn)) {
                        return@forEachIndexed
                    }
                    globalOutbounds.add(tagIn)
                }

                outboundTagsAll[tagIn] = proxyEntity

                if (isBalancer || index == 0) {
                    outboundTags.add(tagIn)
                    if (tagOutbound == TAG_AGENT) {
                        outboundTagsCurrent.add(tagIn)
                    }
                }

                if (proxyEntity.needExternal()) {
                    val localPort = mkPort()
                    chainMap[localPort] = proxyEntity
                    currentOutbound.apply {
                        protocol = "socks"
                        settings = LazyOutboundConfigurationObject(
                            this,
                            SocksOutboundConfigurationObject().apply {
                                servers =
                                    listOf(SocksOutboundConfigurationObject.ServerObject().apply {
                                        address = LOCALHOST
                                        port = localPort
                                    })
                            })
                    }
                } else {
                    currentOutbound.apply {
                        val keepAliveInterval = DataStore.tcpKeepAliveInterval
                        val needKeepAliveInterval = keepAliveInterval !in intArrayOf(0, 15)

                        if (bean is SOCKSBean) {
                            protocol = "socks"
                            settings = LazyOutboundConfigurationObject(this,
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
                                            })
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
                                        sockopt = StreamSettingsObject.SockoptObject().apply {
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
                                            })
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
                                        sockopt = StreamSettingsObject.SockoptObject().apply {
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
                                        vnext =
                                            listOf(VMessOutboundConfigurationObject.ServerObject()
                                                .apply {
                                                    address = bean.serverAddress
                                                    port = bean.serverPort
                                                    users =
                                                        listOf(VMessOutboundConfigurationObject.ServerObject.UserObject()
                                                            .apply {
                                                                id = bean.uuidOrGenerate()
                                                                alterId = bean.alterId
                                                                security =
                                                                    bean.encryption.takeIf { it.isNotBlank() }
                                                                        ?: "auto"
                                                                level = 8
                                                            })
                                                })
                                    })
                            } else if (bean is VLESSBean) {
                                protocol = "vless"
                                settings = LazyOutboundConfigurationObject(this,
                                    VLESSOutboundConfigurationObject().apply {
                                        vnext =
                                            listOf(VLESSOutboundConfigurationObject.ServerObject()
                                                .apply {
                                                    address = bean.serverAddress
                                                    port = bean.serverPort
                                                    users =
                                                        listOf(VLESSOutboundConfigurationObject.ServerObject.UserObject()
                                                            .apply {
                                                                id = bean.uuidOrGenerate()
                                                                encryption = bean.encryption
                                                                level = 8
                                                            })
                                                })
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

                                        if (bean.certificates.isNotBlank()) {
                                            disableSystemRoot = true
                                            certificates =
                                                listOf(TLSObject.CertificateObject().apply {
                                                    usage = "verify"
                                                    certificate = bean.certificates.split("\n")
                                                        .filter { it.isNotBlank() }
                                                })
                                        }

                                        if (bean.pinnedPeerCertificateChainSha256.isNotBlank()) {
                                            pinnedPeerCertificateChainSha256 =
                                                bean.pinnedPeerCertificateChainSha256.split("\n")
                                                    .filter { it.isNotBlank() }
                                        }

                                        if (bean.allowInsecure) {
                                            allowInsecure = true
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
                                                                        path = bean.path.split(
                                                                            ","
                                                                        )
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
                                    "http" -> {
                                        network = "http"

                                        httpSettings = HttpObject().apply {
                                            if (bean.host.isNotBlank()) {
                                                host = bean.host.split(",")
                                            }

                                            path = bean.path.takeIf { it.isNotBlank() } ?: "/"
                                        }
                                    }
                                    "quic" -> {
                                        quicSettings = QuicObject().apply {
                                            security = bean.quicSecurity.takeIf { it.isNotBlank() }
                                                ?: "none"
                                            key = bean.quicKey
                                            header = QuicObject.HeaderObject().apply {
                                                type = bean.headerType.takeIf { it.isNotBlank() }
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
                                    servers =
                                        listOf(ShadowsocksOutboundConfigurationObject.ServerObject()
                                            .apply {
                                                address = bean.serverAddress
                                                port = bean.serverPort
                                                method = bean.method
                                                password = bean.password
                                            })
                                    if (needKeepAliveInterval) {
                                        streamSettings = StreamSettingsObject().apply {
                                            sockopt = StreamSettingsObject.SockoptObject().apply {
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
                                        TrojanOutboundConfigurationObject.ServerObject().apply {
                                                address = bean.serverAddress
                                                port = bean.serverPort
                                                password = bean.password
                                                level = 8
                                            })
                                })
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
                                if (bean.allowInsecure) {
                                    tlsSettings = tlsSettings ?: TLSObject()
                                    tlsSettings.allowInsecure = true
                                }
                            }
                        }
                        if ((isBalancer || index == 0) && proxyEntity.needCoreMux() && DataStore.enableMux) {
                            mux = OutboundObject.MuxObject().apply {
                                enabled = true
                                concurrency = DataStore.muxConcurrency
                            }
                        }
                    }
                }

                currentOutbound.tag = tagIn

                if (!isBalancer && index > 0) {
                    if (!pastExternal) {
                        pastOutbound.proxySettings = OutboundObject.ProxySettingsObject().apply {
                            tag = tagIn
                            transportLayer = true
                        }
                    } else {
                        routing.rules.add(RoutingObject.RuleObject().apply {
                            type = "field"
                            inboundTag = listOf(pastInboundTag)
                            outboundTag = tagIn
                        })
                    }
                }

                if (proxyEntity.needExternal() && !isBalancer && index != profileList.lastIndex) {
                    val mappingPort = mkPort()
                    bean.finalAddress = LOCALHOST
                    bean.finalPort = mappingPort
                    bean.isChain = true

                    inbounds.add(InboundObject().apply {
                        listen = LOCALHOST
                        port = mappingPort
                        tag = "$tagOutbound-mapping-${proxyEntity.id}"
                        protocol = "dokodemo-door"
                        settings = LazyInboundConfigurationObject(this,
                            DokodemoDoorInboundConfigurationObject().apply {
                                address = bean.serverAddress
                                network = "tcp,udp"
                                port = bean.serverPort
                            })

                        pastInboundTag = tag
                    })
                } else if (bean.canMapping() && proxyEntity.needExternal() && !bean.serverAddress.isIpAddress()) {
                    val mappingPort = mkPort()
                    when (bean) {
                        is BrookBean -> {
                            dns.hosts[bean.serverAddress] = LOCALHOST
                        }
                        else -> {
                            bean.finalAddress = LOCALHOST
                        }
                    }
                    bean.finalPort = mappingPort

                    inbounds.add(InboundObject().apply {
                        listen = LOCALHOST
                        port = mappingPort
                        tag = "$tagOutbound-mapping-${proxyEntity.id}"
                        protocol = "dokodemo-door"
                        settings = LazyInboundConfigurationObject(this,
                            DokodemoDoorInboundConfigurationObject().apply {
                                address = bean.serverAddress
                                network = "tcp,udp"
                                port = bean.serverPort
                            })
                        routing.rules.add(RoutingObject.RuleObject().apply {
                            type = "field"
                            inboundTag = listOf(tag)
                            outboundTag = TAG_DIRECT
                        })
                    })

                }

                outbounds.add(currentOutbound)
                chainOutbounds.add(currentOutbound)

                pastExternal = proxyEntity.needExternal()
                pastOutbound = currentOutbound

            }

            if (isBalancer) {
                if (routing.balancers == null) routing.balancers = ArrayList()
                routing.balancers.add(RoutingObject.BalancerObject().apply {
                    tag = "balancer-$tagOutbound"
                    selector = chainOutbounds.map { it.tag }
                    if (observatory == null) observatory = ObservatoryObject().apply {
                        probeUrl = DataStore.connectionTestURL
                        val testInterval = DataStore.probeInterval
                        if (testInterval > 0) {
                            probeInterval = "${testInterval}s"
                        }
                    }
                    if (observatory.subjectSelector == null) observatory.subjectSelector = HashSet()
                    observatory.subjectSelector.addAll(chainOutbounds.map { it.tag })
                    strategy = RoutingObject.BalancerObject.StrategyObject().apply {
                        type = balancerStrategy().takeIf { it.isNotBlank() } ?: "random"
                    }
                })
                if (tagOutbound == TAG_AGENT) {
                    routing.rules.add(RoutingObject.RuleObject().apply {
                        type = "field"
                        inboundTag = mutableListOf()

                        if (!forTest) inboundTag.add(TAG_SOCKS)
                        if (requireHttp) inboundTag.add(TAG_HTTP)
                        if (requireTransproxy) inboundTag.add(TAG_TRANS)
                        balancerTag = "balancer-$tagOutbound"
                    })
                    outbounds.add(0, OutboundObject().apply {
                        protocol = "loopback"
                        settings = LazyOutboundConfigurationObject(
                            this,
                            LoopbackOutboundConfigurationObject().apply {
                                inboundTag = TAG_SOCKS
                            })
                    })
                }
            }

            return chainOutbound

        }

        val tagProxy = buildChain(
            TAG_AGENT, proxies, proxy.balancerBean != null
        ) { proxy.balancerBean!!.strategy }
        val balancerMap = mutableMapOf<Long, String>()
        val tagMap = mutableMapOf<Long, String>()
        extraProxies.forEach { (key, entities) ->
            val (id, balancer) = key
            val (isBalancer, strategy) = balancer
            tagMap[id] = buildChain("$TAG_AGENT-$id", entities, isBalancer, strategy::value)
            if (isBalancer) {
                balancerMap[id] = "balancer-$TAG_AGENT-$id"
            }
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
                when {
                    rule.reverse -> inboundTag = listOf("reverse-${rule.id}")
                    balancerMap.containsKey(rule.outbound) -> {
                        balancerTag = balancerMap[rule.outbound]
                    }
                    else -> outboundTag = when (val outId = rule.outbound) {
                        0L -> TAG_AGENT
                        -1L -> TAG_DIRECT
                        -2L -> TAG_BLOCK
                        else -> if (outId == proxy.id) tagProxy else tagMap[outId]
                    }
                }
            })
            if (rule.reverse) {
                outbounds.add(OutboundObject().apply {
                    tag = "reverse-out-${rule.id}"
                    protocol = "freedom"
                    settings = LazyOutboundConfigurationObject(
                        this,
                        FreedomOutboundConfigurationObject().apply {
                            redirect = rule.redirect
                        })
                })
                if (reverse == null) {
                    reverse = ReverseObject().apply {
                        bridges = ArrayList()
                    }
                }
                reverse.bridges.add(ReverseObject.BridgeObject().apply {
                    tag = "reverse-${rule.id}"
                    domain = rule.domains.substringAfter("full:")
                })
                routing.rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    inboundTag = listOf("reverse-${rule.id}")
                    outboundTag = "reverse-out-${rule.id}"
                })
            }

        }

        if (requireWs) {
            browserForwarder = BrowserForwarderObject().apply {
                listenAddr = LOCALHOST
                listenPort = mkPort()
            }
        }

        outbounds.add(OutboundObject().apply {
            tag = TAG_DIRECT
            protocol = "freedom"
            settings =
                LazyOutboundConfigurationObject(this, FreedomOutboundConfigurationObject().apply {
                    when (ipv6Mode) {
                        IPv6Mode.DISABLE -> domainStrategy = "UseIPv4"
                        IPv6Mode.ONLY -> domainStrategy = "UseIPv6"
                        else -> if (useFakeDns) {
                            domainStrategy = "UseIP"
                        }
                    }
                })
        })

        outbounds.add(OutboundObject().apply {
            tag = TAG_BLOCK
            protocol = "blackhole"

            settings =
                LazyOutboundConfigurationObject(this, BlackholeOutboundConfigurationObject().apply {
                    response = BlackholeOutboundConfigurationObject.ResponseObject().apply {
                        type = "http"
                    }
                })
        })

        val bypassIP = HashSet<String>()
        val bypassDomain = HashSet<String>()

        (proxies + extraProxies.values.flatten()).filter { !it.requireBean().isChain }.forEach {
            it.requireBean().apply {
                if (!serverAddress.isIpAddress()) {
                    bypassDomain.add("full:$serverAddress")
                } else {
                    bypassIP.add(serverAddress)
                }
            }
        }

        if (bypassIP.isNotEmpty()) {
            routing.rules.add(0, RoutingObject.RuleObject().apply {
                type = "field"
                ip = bypassIP.toList()
                outboundTag = TAG_DIRECT
            })
        }
        /*if (bypassDomain.isNotEmpty()) {
            routing.rules.add(0, RoutingObject.RuleObject().apply {
                type = "field"
                domain = bypassDomain.toList()
                outboundTag = TAG_DIRECT
            })
        }*/

        if (dnsMode != DnsMode.SYSTEM) {
            inbounds.add(InboundObject().apply {
                tag = TAG_DNS_IN
                listen = LOCALHOST
                port = DataStore.localDNSPort
                protocol = "dokodemo-door"
                settings = LazyInboundConfigurationObject(
                    this,
                    DokodemoDoorInboundConfigurationObject().apply {
                        address = if (!localDns.first().isIpAddress()) {
                            "1.1.1.1"
                        } else {
                            localDns.first()
                        }
                        network = "tcp,udp"
                        port = 53
                    })

            })
            outbounds.add(OutboundObject().apply {
                protocol = "dns"
                tag = TAG_DNS_OUT
                if (useLocalDns) {
                    settings = LazyOutboundConfigurationObject(
                        this,
                        DNSOutboundConfigurationObject().apply {
                            var dns = localDns.first()
                            if (dns.contains(":")) {
                                val lPort = dns.substringAfterLast(":")
                                dns = dns.substringBeforeLast(":")
                                if (NumberUtil.isInteger(lPort)) {
                                    port = lPort.toInt()
                                }
                            }
                            if (dns.isIpAddress()) {
                                address = dns
                            } else if (dns.contains("://")) {
                                network = "tcp"
                                address = dns.substringAfter("://")
                            }
                        })
                }
            })

            if (useLocalDns) {

                for (dns in localDns) {
                    if (!dns.isIpAddress()) continue
                    routing.rules.add(0, RoutingObject.RuleObject().apply {
                        type = "field"
                        outboundTag = TAG_AGENT
                        ip = listOf(dns)
                    })
                }

                if (enableDomesticDns) {
                    for (dns in domesticDns) {
                        if (!dns.isIpAddress()) continue

                        routing.rules.add(0, RoutingObject.RuleObject().apply {
                            type = "field"
                            outboundTag = TAG_DIRECT
                            ip = listOf(dns)
                        })
                    }

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
                                if (bypassIP.isNotEmpty()) {
                                    expectIPs = bypassIP.toList()
                                }
                                if (bypassDomain.isNotEmpty()) {
                                    domains = bypassDomain.toList()
                                }
                            }
                        })
                    }
                }

            } else if (dnsMode == DnsMode.SYSTEM) {
                for (dns in systemDns) {
                    routing.rules.add(0, RoutingObject.RuleObject().apply {
                        type = "field"
                        outboundTag = TAG_AGENT
                        ip = listOf(dns)
                    })
                }
            }

            if (dnsMode == DnsMode.FAKEDNS_LOCAL) { //                val domainsToRoute = dns.servers.flatMap { it.valueY?.domains ?: listOf() }
                //                    .toHashSet().toList()
                dns.servers.add(
                    0, /*if (domainsToRoute.isNotEmpty()) {
                    DnsObject.StringOrServerObject().apply {
                        valueY = DnsObject.ServerObject().apply {
                            address = "fakedns"
                            domains = domainsToRoute
                        }
                    }
                } else {*/
                    DnsObject.StringOrServerObject().apply {
                        valueX = "fakedns"
                    }/*}*/
                )
            }

            routing.rules.add(0, RoutingObject.RuleObject().apply {
                type = "field"
                inboundTag = listOf(TAG_DNS_IN)
                outboundTag = TAG_DNS_OUT
            })
        }

        stats = emptyMap()

        val apiPort = DataStore.apiPort

        api = ApiObject().apply {
            tag = TAG_API
            services = mutableListOf()
            if (USE_STATS_SERVICE) {
                services.add("StatsService")
            }
            if (!observatory?.subjectSelector.isNullOrEmpty()) {
                services.add("ObservatoryService")
            }
        }

        if (forTest || api.services.isEmpty()) {
            api = null
        } else {
            inbounds.add(InboundObject().apply {
                protocol = "dokodemo-door"
                listen = LOCALHOST
                port = apiPort
                tag = TAG_API_IN
                settings = LazyInboundConfigurationObject(this,
                    DokodemoDoorInboundConfigurationObject().apply {
                        address = LOCALHOST
                        port = apiPort
                        network = "tcp"
                    })
            })

            routing.rules.add(0, RoutingObject.RuleObject().apply {
                type = "field"
                inboundTag = listOf(TAG_API_IN)
                outboundTag = TAG_API
            })
        }
    }.let {
        V2rayBuildResult(
            gson.toJson(it),
            indexMap,
            requireWs,
            if (requireWs) it.browserForwarder.listenPort else 0,
            outboundTags,
            outboundTagsCurrent,
            outboundTagsAll,
            TAG_DIRECT,
            !it.api?.services.isNullOrEmpty(),
            it.observatory?.subjectSelector ?: HashSet()
        )
    }

}

fun buildCustomConfig(
    proxy: ProxyEntity, forTest: Boolean = false, testPort: Int = 0
): V2rayBuildResult {

    val bind = if (!forTest && DataStore.allowAccess) "0.0.0.0" else LOCALHOST
    val trafficSniffing = DataStore.trafficSniffing

    val bean = proxy.configBean!!
    val config = JSONObject(bean.content)
    val inbounds = config.getJSONArray("inbounds")?.filterIsInstance<JSONObject>()
        ?.map { gson.fromJson(it.toString(), InboundObject::class.java) }?.toMutableList()
        ?: ArrayList()

    val dnsArr = config.getJSONObject("dns")?.getJSONArray("servers")?.map {
        if (it is String) DnsObject.StringOrServerObject().apply {
            valueX = it
        } else DnsObject.StringOrServerObject().apply {
            valueY = gson.fromJson(it.toString(), DnsObject.ServerObject::class.java)
        }
    }
    val ipv6Mode = if (forTest) IPv6Mode.ENABLE else DataStore.ipv6Mode
    var useFakeDns = false

    val requireHttp =
        forTest || Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || DataStore.requireHttp
    val requireTransproxy = if (forTest) false else DataStore.requireTransproxy

    val dnsInbound = inbounds.find { it.tag == TAG_DNS_IN }?.also { inbound ->
        inbound.listen = bind
        inbound.port = if (forTest) mkPort() else DataStore.localDNSPort

        if (!forTest) if (dnsArr?.any { it.valueX == "fakedns" } == true) {
            DataStore.dnsModeFinal = DnsMode.FAKEDNS_LOCAL
            useFakeDns = true

            config.set("fakedns", JSONArray().apply {
                add(JSONObject(gson.toJson(FakeDnsObject().apply {
                    ipPool = "${VpnService.FAKEDNS_VLAN4_CLIENT}/15"
                    poolSize = 65535
                })))
                if (ipv6Mode != IPv6Mode.DISABLE) {
                    add(JSONObject(gson.toJson(FakeDnsObject().apply {
                        ipPool = "${VpnService.FAKEDNS_VLAN6_CLIENT}/18"
                        poolSize = 65535
                    })))
                }
            })
        } else {
            DataStore.dnsModeFinal = DnsMode.LOCAL
        }
    }

    if (!forTest) if (dnsInbound == null) {
        DataStore.dnsModeFinal = DnsMode.SYSTEM

        val dns = dnsArr?.filter {
            it.valueX != null && it.valueX.isIpAddress() || it.valueY != null && it.valueY.address.isIpAddress() && it.valueY.port in arrayOf(
                null, 53
            ) && it.valueY.domains.isNullOrEmpty() && it.valueY.expectIPs.isNullOrEmpty()
        }?.map { it.valueX ?: it.valueY.address }

        if (dns.isNullOrEmpty()) {
            DataStore.systemDnsFinal = DataStore.systemDns
        } else {
            DataStore.systemDnsFinal = dns.joinToString("\n")
        }
    }

    var socksInbound = inbounds.find { it.tag == TAG_SOCKS }?.apply {
        if (protocol != "socks") error("Inbound $tag with type $protocol, excepted socks.")
    }

    if (socksInbound == null) {
        val socksInbounds = inbounds.filter { it.protocol == "socks" }
        if (socksInbounds.size == 1) {
            socksInbound = socksInbounds[0]
        }
    }

    if (socksInbound != null) {
        socksInbound.apply {
            listen = bind
            port = if (forTest) mkPort() else DataStore.socksPort
        }
    } else if (!forTest) {
        inbounds.add(InboundObject().apply {
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
                    destOverride = if (useFakeDns) {
                        listOf("fakedns", "http", "tls")
                    } else {
                        listOf("http", "tls")
                    }
                    metadataOnly = false
                }
            }
        })
    }

    if (requireHttp) {

        var httpInbound = inbounds.find { it.tag == TAG_HTTP }?.apply {
            if (protocol != "http") error("Inbound $tag with type $protocol, excepted http.")

        }

        if (httpInbound == null) {
            val httpInbounds = inbounds.filter { it.protocol == "http" }
            if (httpInbounds.size == 1) {
                httpInbound = httpInbounds[0]
            }
        }

        if (httpInbound != null) {
            httpInbound.apply {
                listen = bind
                port = if (forTest) testPort else DataStore.httpPort
            }
        } else {
            inbounds.add(InboundObject().apply {

                tag = TAG_HTTP
                listen = bind
                port = if (forTest) testPort else DataStore.httpPort
                protocol = "http"
                settings =
                    LazyInboundConfigurationObject(this, HTTPInboundConfigurationObject().apply {
                        allowTransparent = true
                        userLevel = 8
                    })
                if (trafficSniffing) {
                    sniffing = InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = if (useFakeDns) {
                            listOf("fakedns", "http", "tls")
                        } else {
                            listOf("http", "tls")
                        }
                        metadataOnly = false
                    }
                }
            })
        }

    }

    if (requireTransproxy) {

        val transInbound = inbounds.find { it.tag == TAG_TRANS }?.apply {
            if (protocol != "dokodemo-door") error("Inbound $tag with type $protocol, excepted dokodemo-door.")
            listen = bind
            port = DataStore.transproxyPort
        }

        if (transInbound == null) {

            inbounds.add(InboundObject().apply {
                tag = TAG_TRANS
                listen = bind
                port = DataStore.transproxyPort
                protocol = "dokodemo-door"
                settings = LazyInboundConfigurationObject(
                    this,
                    DokodemoDoorInboundConfigurationObject().apply {
                        network = "tcp,udp"
                        followRedirect = true
                        userLevel = 8
                    })
                if (trafficSniffing) {
                    sniffing = InboundObject.SniffingObject().apply {
                        enabled = true
                        destOverride = if (useFakeDns) {
                            listOf("fakedns", "http", "tls")
                        } else {
                            listOf("http", "tls")
                        }
                        metadataOnly = false
                    }
                }
                when (DataStore.transproxyMode) {
                    1 -> streamSettings = StreamSettingsObject().apply {
                        sockopt = StreamSettingsObject.SockoptObject().apply {
                            tproxy = "tproxy"
                        }
                    }
                }
            })

        }

    }

    if (!forTest) {
        config["stats"] = JSONObject()
        (config.getJSONObject("policy") ?: JSONObject().also {
            config["policy"] = it
        })["system"] = JSONObject(gson.toJson(PolicyObject.SystemPolicyObject().apply {
            statsOutboundDownlink = true
            statsOutboundUplink = true
        }))
    }

    var requireWs = false
    var wsPort = 0
    if (config.contains("browserForwarder")) {
        config["browserForwarder"] = JSONObject(gson.toJson(BrowserForwarderObject().apply {
            requireWs = true
            listenAddr = LOCALHOST
            listenPort = mkPort()
            wsPort = listenPort
        }))
    }

    val outbounds = try {
        config.getJSONArray("outbounds")?.filterIsInstance<JSONObject>()?.map {
            gson.fromJson(it.toString().takeIf { it.isNotBlank() } ?: "{}",
                OutboundObject::class.java)
        }?.toMutableList()
    } catch (e: JsonSyntaxException) {
        null
    }
    var flushOutbounds = false

    val outboundTags = ArrayList<String>()
    val firstOutbound = outbounds?.get(0)
    if (firstOutbound != null) {
        if (firstOutbound.tag == null) {
            firstOutbound.tag = TAG_AGENT
            outboundTags.add(TAG_AGENT)
            flushOutbounds = true
        } else {
            outboundTags.add(firstOutbound.tag)
        }
    }

    var directTag = ""
    val directOutbounds = outbounds?.filter { it.protocol == "freedom" }
    if (!directOutbounds.isNullOrEmpty()) {
        val directOutbound = if (directOutbounds.size == 1) {
            directOutbounds[0]
        } else {
            val directOutboundsWithTag = directOutbounds.filter { it.tag != null }
            if (directOutboundsWithTag.isNotEmpty()) {
                directOutboundsWithTag[0]
            } else {
                directOutbounds[0]
            }
        }
        if (directOutbound.tag.isNullOrBlank()) {
            directOutbound.tag = TAG_DIRECT
            flushOutbounds = true
        }
        directTag = directOutbound.tag
    }

    var useApi = false
    var flushApi = false
    val api = config.getJSONObject("api")?.let {
        gson.fromJson(it.toString(), ApiObject::class.java)
    } ?: ApiObject()

    var flushRouting = false
    val routing = (config.getJSONObject("routing")?.let {
        gson.fromJson(it.toString(), RoutingObject::class.java)
    } ?: RoutingObject().apply {
        domainStrategy = DataStore.domainStrategy
        domainMatcher = DataStore.domainMatcher
    }).apply {
        if (rules == null) rules = mutableListOf()
    }

    if (USE_STATS_SERVICE) {
        useApi = true

        if (api.tag.isNullOrBlank()) {
            api.tag = TAG_API
            flushApi = true
        }

        var apiRule = routing.rules.find { it.outboundTag == api.tag }
        if (apiRule == null) {
            apiRule = RoutingObject.RuleObject().apply {
                type = "field"
                inboundTag = listOf(TAG_API_IN)
                outboundTag = api.tag
            }
            routing.rules.add(apiRule)
            flushRouting = true
        }

        val apiPort = DataStore.apiPort

        val apiInTag = apiRule.inboundTag[0]
        val apiInbound = inbounds.find { it.tag == apiInTag }?.apply {
            if (protocol != "dokodemo-door") error("Inbound $tag with type $protocol, excepted dokodemo-door.")
            port = apiPort
        }

        if (apiInbound == null) {
            inbounds.add(InboundObject().apply {
                protocol = "dokodemo-door"
                listen = LOCALHOST
                port = apiPort
                tag = apiInTag
                settings = LazyInboundConfigurationObject(this,
                    DokodemoDoorInboundConfigurationObject().apply {
                        address = LOCALHOST
                        port = apiPort
                        network = "tcp"
                    })
            })
        }

    } else if (!api.services.isNullOrEmpty()) {
        useApi = true
    }

    if (flushApi) config["api"] = JSONObject(gson.toJson(api))
    if (flushRouting) config["routing"] = JSONObject(gson.toJson(routing))

    inbounds.forEach { it.init() }
    config["inbounds"] = JSONArray(inbounds.map { JSONObject(gson.toJson(it)) })
    if (flushOutbounds) {
        outbounds!!.forEach { it.init() }
        config["outbounds"] = JSONArray(outbounds.map { JSONObject(gson.toJson(it)) })
    }

    val observatoryTags = mutableSetOf<String>()
    if (api.services?.contains("ObservatoryService") == true) {
        config.getJSONObject("observatory")?.getJSONArray("subjectSelector")?.map {
            it.toString()
        }?.toHashSet()?.also {
            observatoryTags.addAll(it)
        }
    }

    return V2rayBuildResult(
        config.toStringPretty(),
        ArrayList(),
        requireWs,
        wsPort,
        outboundTags,
        outboundTags,
        hashMapOf(),
        directTag,
        useApi,
        observatoryTags
    )

}
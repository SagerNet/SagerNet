/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.fmt.v2ray.pb

import android.os.Build
import cn.hutool.core.util.NumberUtil
import com.v2ray.core.OutboundHandlerConfig
import com.v2ray.core.app.dns.fakedns.fakeDnsPool
import com.v2ray.core.app.dns.fakedns.fakeDnsPoolMulti
import com.v2ray.core.app.policy.SystemPolicyKt.stats
import com.v2ray.core.app.policy.systemPolicy
import com.v2ray.core.app.proxyman.*
import com.v2ray.core.app.reverse.bridgeConfig
import com.v2ray.core.common.log.Severity
import com.v2ray.core.common.net.Network
import com.v2ray.core.common.net.endpoint
import com.v2ray.core.common.protocol.serverEndpoint
import com.v2ray.core.inboundHandlerConfig
import com.v2ray.core.outboundHandlerConfig
import com.v2ray.core.proxy.freedom.destinationOverride
import com.v2ray.core.proxy.socks.AuthType
import com.v2ray.core.transport.internet.SocketConfig
import com.v2ray.core.transport.internet.socketConfig
import com.v2ray.core.transport.internet.streamConfig
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.bg.ForegroundDetectorService
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.*
import io.nekohasekai.sagernet.fmt.brook.BrookBean
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.internal.BalancerBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.DnsObject
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.RoutingObject
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.RoutingObject.BalancerObject
import io.nekohasekai.sagernet.fmt.v2ray.pb.proxy.newSocks5Client
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.isRunning
import io.nekohasekai.sagernet.ktx.mkPort
import io.nekohasekai.sagernet.utils.PackageCache
import libcore.Libcore
import com.v2ray.core.app.browserforwarder.config as browserforwarderConfig
import com.v2ray.core.app.dispatcher.Config as DispatcherConfig
import com.v2ray.core.app.log.config as logConfig
import com.v2ray.core.app.policy.config as policyConfig
import com.v2ray.core.app.reverse.Config as ReverseConfig
import com.v2ray.core.app.stats.Config as StatsConfig
import com.v2ray.core.config as v2rayConfig
import com.v2ray.core.proxy.blackhole.Config as BlackholeConfig
import com.v2ray.core.proxy.dns.config as dnsConfig
import com.v2ray.core.proxy.dokodemo.config as dokodemoConfig
import com.v2ray.core.proxy.freedom.Config as FreedomConfig
import com.v2ray.core.proxy.freedom.config as freedomConfig
import com.v2ray.core.proxy.http.serverConfig as httpServerConfig
import com.v2ray.core.proxy.socks.serverConfig as socksServerConfig
import com.v2ray.core.transport.internet.websocket.Config as WebsocketConfig

fun buildV2rayProto(proxy: ProxyEntity, forTest: Boolean): V2rayBuildResult {

    val outboundTags = ArrayList<String>()
    val outboundTagsCurrent = ArrayList<String>()
    val outboundTagsAll = HashMap<String, ProxyEntity>()
    val globalOutbounds = ArrayList<String>()

    fun ProxyEntity.resolveChain(): MutableList<ProxyEntity> {
        val bean = requireBean()
        if (bean is ChainBean) {
            val beans = SagerDatabase.proxyDao.getEntities(bean.proxies)
            val beansMap = beans.associateBy { it.id }
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

            val beansMap = beans.associateBy { it.id }
            val beanList = ArrayList<ProxyEntity>()
            for (proxyId in beansMap.keys) {
                val item = beansMap[proxyId] ?: continue
                if (item.id == id) continue
                when (item.type) {
                    ProxyEntity.TYPE_BALANCER -> error("Nested balancers are not supported")
                    ProxyEntity.TYPE_CHAIN -> error("Chain is incompatible with balancer")
                }
                beanList.add(item)
            }
            return beanList
        }
        return mutableListOf(this)
    }

    val proxies = proxy.resolveChain()
    val extraRules = if (forTest) listOf() else SagerDatabase.rulesDao.enabledRules()
    val extraProxies = if (forTest) mapOf() else SagerDatabase.proxyDao.getEntities(extraRules.mapNotNull { rule ->
        rule.outbound.takeIf { it > 0 && it != proxy.id }
    }.toHashSet().toList()).associate {
        (it.id to ((it.type == ProxyEntity.TYPE_BALANCER) to lazy {
            it.balancerBean!!.strategy
        })) to it.resolveChain()
    }

    val allowAccess = DataStore.allowAccess
    val bind = if (!forTest && allowAccess) "0.0.0.0" else LOCALHOST

    val remoteDns = DataStore.remoteDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val directDNS = DataStore.directDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val enableDnsRouting = DataStore.enableDnsRouting
    val useFakeDns = DataStore.enableFakeDns
    val trafficSniffing = DataStore.trafficSniffing
    val indexMap = ArrayList<V2rayBuildResult.IndexEntity>()
    var requireWs = false
    var wsPort = 0
    val requireHttp = !forTest && (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || DataStore.requireHttp)
    val requireTransproxy = if (forTest) false else DataStore.requireTransproxy
    val ipv6Mode = if (forTest) IPv6Mode.ENABLE else DataStore.ipv6Mode
    var dumpUid = false
    val alerts = mutableListOf<Pair<Int, String>>()

    val inboundSniff = sniffingConfig {
        enabled = true
        destinationOverride.addAll(
            if (useFakeDns) listOf("fakedns", "http", "tls") else listOf("http", "tls")
        )
        metadataOnly = false
    }

    val dns = DnsObject()
    val routing = RoutingObject()

    val config = v2rayConfig {

        app.add(typedMessage {
            logConfig {
                errorLogLevel = if (!forTest && DataStore.enableLog) Severity.Debug else Severity.Error
            }
        })

        app.add(typedMessage { DispatcherConfig.getDefaultInstance() })
        app.add(typedMessage { InboundConfig.getDefaultInstance() })
        app.add(typedMessage { OutboundConfig.getDefaultInstance() })

        dns.apply {
            hosts = DataStore.hosts.split("\n")
                .filter { it.isNotBlank() }
                .associate { it.substringBefore(" ") to it.substringAfter(" ") }
                .toMutableMap()
            servers = mutableListOf()

            servers.addAll(remoteDns.map {
                DnsObject.StringOrServerObject().apply {
                    valueX = it
                }
            })

            when (ipv6Mode) {
                IPv6Mode.DISABLE -> {
                    queryStrategy = "UseIPv4"
                }
                IPv6Mode.ONLY -> {
                    queryStrategy = "UseIPv6"
                }
            }
        }

        if (useFakeDns) app.add(typedMessage {
            fakeDnsPoolMulti {
                pools.add(fakeDnsPool {
                    ipPool = "${VpnService.FAKEDNS_VLAN4_CLIENT}/15"
                    lruSize = 2048
                })
                if (ipv6Mode != IPv6Mode.DISABLE) {
                    pools.add(fakeDnsPool {
                        ipPool = "${VpnService.FAKEDNS_VLAN6_CLIENT}/18"
                        lruSize = 2048
                    })
                }

            }
        })

        app.add(typedMessage {
            policyConfig {

                system = systemPolicy {
                    stats = stats {
                        outboundUplink = true
                        outboundDownlink = true
                    }
                }
            }
        })

        if (!forTest) inbound.add(inboundHandlerConfig {
            tag = TAG_SOCKS
            proxySettings = typedMessage {
                socksServerConfig {
                    authType = AuthType.NO_AUTH
                    udpEnabled = true
                    userLevel = defaultLevel
                }
            }
            receiverSettings = typedMessage {
                receiverConfig {
                    listen = bind.toIpOrDomain()
                    portRange = DataStore.socksPort.toPortRange()
                    if (trafficSniffing || useFakeDns) sniffingSettings = inboundSniff
                }
            }
        })

        if (requireHttp) inbound.add(inboundHandlerConfig {
            tag = TAG_HTTP
            proxySettings = typedMessage {
                httpServerConfig {
                    allowTransparent = true
                    userLevel = defaultLevel
                }
            }
            receiverSettings = typedMessage {
                receiverConfig {
                    listen = bind.toIpOrDomain()
                    portRange = DataStore.httpPort.toPortRange()
                    if (trafficSniffing || useFakeDns) sniffingSettings = inboundSniff
                }
            }
        })

        if (requireTransproxy) inbound.add(inboundHandlerConfig {
            tag = TAG_TRANS
            proxySettings = typedMessage {
                dokodemoConfig {
                    networks.addAll(listOf(Network.TCP, Network.UDP))
                    followRedirect = true
                    userLevel = defaultLevel
                }
            }
            receiverSettings = typedMessage {
                receiverConfig {
                    listen = bind.toIpOrDomain()
                    portRange = DataStore.httpPort.toPortRange()
                    if (trafficSniffing || useFakeDns) sniffingSettings = inboundSniff

                    when (DataStore.transproxyMode) {
                        1 -> streamSettings = streamConfig {
                            socketSettings = socketConfig {
                                tproxy = SocketConfig.TProxyMode.TProxy
                            }
                        }
                    }
                }
            }
        })

        routing.apply {
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

            if (DataStore.bypassLan && (requireHttp || DataStore.bypassLanInCoreOnly)) {
                rules.add(RoutingObject.RuleObject().apply {
                    type = "field"
                    outboundTag = TAG_BYPASS
                    ip = listOf("geoip:private")
                })
            }
        }

        val needIncludeSelf = proxy.balancerBean == null && proxies.size > 1 || extraProxies.any { (key, value) ->
            val (_, balancer) = key
            val (isBalancer, _) = balancer
            isBalancer && value.size > 1
        }

        var rootBalancer: RoutingObject.RuleObject? = null

        fun buildChain(
            tagOutbound: String,
            profileList: List<ProxyEntity>,
            isBalancer: Boolean,
            balancerStrategy: (() -> String)
        ): String {
            var pastExternal = false
            lateinit var pastOutbound: OutboundHandlerConfig
            lateinit var currentOutbound: OutboundHandlerConfig
            lateinit var pastInboundTag: String
            val chainMap = LinkedHashMap<Int, ProxyEntity>()
            indexMap.add(V2rayBuildResult.IndexEntity(isBalancer, chainMap))
            val chainOutbounds = ArrayList<OutboundHandlerConfig>()
            var chainOutbound = ""

            profileList.forEachIndexed { index, proxyEntity ->
                val bean = proxyEntity.requireBean()

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
                    currentOutbound = outboundHandlerConfig {
                        proxySettings = newSocks5Client(LOCALHOST, localPort)
                    }
                } else {
                    currentOutbound = proxyEntity.buildOutbound()
                }

                if ((isBalancer || index == 0) && proxyEntity.needCoreMux() && DataStore.enableMux) {
                    currentOutbound = currentOutbound.toBuilder().setSenderSettings(typedMessage {
                        if (currentOutbound.hasSenderSettings()) {
                            SenderConfig.parseFrom(currentOutbound.senderSettings.value)
                        } else {
                            SenderConfig.getDefaultInstance()
                        }.toBuilder().apply {
                            multiplexSettings = multiplexingConfig {
                                enabled = true
                                concurrency = DataStore.muxConcurrency
                            }
                        }.build()
                    }).build()
                }

                currentOutbound.tag
                currentOutbound = currentOutbound.toBuilder().setTag(tagIn).build()

                if (proxyEntity.needExternal() && !isBalancer && index != profileList.lastIndex) {
                    val mappingPort = mkPort()
                    when (bean) {
                        is BrookBean -> dns.hosts[bean.serverAddress] = LOCALHOST
                        else -> bean.finalAddress = LOCALHOST
                    }
                    bean.finalPort = mappingPort
                    bean.isChain = true

                    inbound.add(inboundHandlerConfig {
                        tag = "$tagOutbound-mapping-${proxyEntity.id}"
                        proxySettings = typedMessage {
                            dokodemoConfig {
                                networks.addAll(bean.networkProto())
                                address = bean.serverAddress.toIpOrDomain()
                                port = bean.serverPort
                                userLevel = defaultLevel
                            }
                        }
                        receiverSettings = typedMessage {
                            receiverConfig {
                                listen = bind.toIpOrDomain()
                                portRange = mappingPort.toPortRange()
                            }
                        }
                    })
                } else if (bean.canMapping() && proxyEntity.needExternal() && needIncludeSelf) {
                    val mappingPort = mkPort()
                    when (bean) {
                        is BrookBean -> dns.hosts[bean.serverAddress] = LOCALHOST
                        else -> bean.finalAddress = LOCALHOST
                    }
                    bean.finalPort = mappingPort

                    inbound.add(inboundHandlerConfig {
                        val tagMapping = "$tagOutbound-mapping-${proxyEntity.id}"
                        tag = tagMapping
                        routing.rules.add(RoutingObject.RuleObject().apply {
                            type = "field"
                            inboundTag = listOf(tag)
                            outboundTag = TAG_DIRECT
                        })
                        proxySettings = typedMessage {
                            dokodemoConfig {
                                networks.addAll(bean.networkProto())
                                address = bean.serverAddress.toIpOrDomain()
                                port = bean.serverPort
                                userLevel = defaultLevel
                            }
                        }
                        receiverSettings = typedMessage {
                            receiverConfig {
                                listen = bind.toIpOrDomain()
                                portRange = mappingPort.toPortRange()
                            }
                        }
                    })

                }

                outbound.add(currentOutbound)
                chainOutbounds.add(currentOutbound)
                pastExternal = proxyEntity.needExternal()
                pastOutbound = currentOutbound
            }

            if (isBalancer) {
                if (routing.balancers == null) routing.balancers = ArrayList()

                if (routing.balancers == null) routing.balancers = ArrayList()
                routing.balancers.add(BalancerObject().apply {
                    tag = "balancer-$tagOutbound"
                    selector = chainOutbounds.map { it.tag }
                    /*if (observatory == null) observatory = V2RayConfig.ObservatoryObject().apply {
                        probeUrl = DataStore.connectionTestURL
                        val testInterval = DataStore.probeInterval
                        if (testInterval > 0) {
                            probeInterval = "${testInterval}s"
                        }
                        enableConcurrency = true
                    }
                    if (observatory.subjectSelector == null) observatory.subjectSelector = HashSet()
                    observatory.subjectSelector.addAll(chainOutbounds.map { it.tag })*/
                    strategy = BalancerObject.StrategyObject().apply {
                        type = balancerStrategy().takeIf { it.isNotBlank() } ?: "random"
                    }
                })
                if (tagOutbound == TAG_AGENT) {
                    rootBalancer = RoutingObject.RuleObject().apply {
                        type = "field"
                        inboundTag = mutableListOf()

                        if (!forTest) {
                            inboundTag.add(TAG_SOCKS)
                        }
                        if (requireHttp) inboundTag.add(TAG_HTTP)
                        if (requireTransproxy) inboundTag.add(TAG_TRANS)
                        balancerTag = "balancer-$tagOutbound"
                    }
                    /* outbounds.add(0, OutboundObject().apply {
                         protocol = "loopback"
                         settings = LazyOutboundConfigurationObject(this,
                             LoopbackOutboundConfigurationObject().apply {
                                 inboundTag = TAG_SOCKS
                             })
                     })*/
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

        val notVpn = DataStore.serviceMode != Key.MODE_VPN
        val foregroundDetectorServiceStarted = ForegroundDetectorService::class.isRunning()

        val reverse = ReverseConfig.newBuilder()
        var needReverse = false

        for (rule in extraRules) {
            if (rule.packages.isNotEmpty() || rule.appStatus.isNotEmpty()) {
                dumpUid = true
                if (notVpn) {
                    alerts.add(0 to rule.displayName())
                    continue
                }
            }
            if (rule.appStatus.isNotEmpty() && !foregroundDetectorServiceStarted) {
                alerts.add(1 to rule.displayName())
            }
            routing.rules.add(RoutingObject.RuleObject().apply {
                type = "field"
                if (rule.packages.isNotEmpty()) {
                    PackageCache.awaitLoadSync()
                    uidList = rule.packages.map {
                        PackageCache[it]?.takeIf { uid -> uid >= 10000 } ?: 1000
                    }.toHashSet().toList()
                }
                if (rule.appStatus.isNotEmpty()) {
                    appStatus = rule.appStatus
                }

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
                        0L -> tagProxy
                        -1L -> TAG_BYPASS
                        -2L -> TAG_BLOCK
                        else -> if (outId == proxy.id) tagProxy else tagMap[outId]
                    }
                }
            })



            if (rule.reverse) {
                needReverse = true

                outbound.add(outboundHandlerConfig {
                    tag = "reverse-out-${rule.id}"
                    proxySettings = typedMessage {
                        freedomConfig {
                            destinationOverride = destinationOverride {
                                server = serverEndpoint {
                                    address = rule.redirect.substringBeforeLast(":").toIpOrDomain()
                                    port = rule.redirect.substringAfterLast(":").toInt()
                                }
                            }
                        }
                    }
                })
                reverse.addBridgeConfig(bridgeConfig {
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

        requireWs = outbound.filter { it.hasSenderSettings() }
            .map { it.senderSettings.unpack(SenderConfig::class.java) }
            .mapNotNull { it.streamSettings }
            .filter { it.protocolName == "ws" }
            .flatMap { it.transportSettingsList }
            .map { it.settings }
            .filter { it.`is`(WebsocketConfig::class.java) }
            .map { it.unpack(WebsocketConfig::class.java) }
            .any { it.useBrowserForwarding }

        if (requireWs) app.add(typedMessage {
            browserforwarderConfig {
                listenAddr = LOCALHOST
                wsPort = mkPort()
                listenPort = wsPort
            }
        })

        for (freedom in arrayOf(TAG_DIRECT, TAG_BYPASS)) outbound.add(outboundHandlerConfig {
            proxySettings = typedMessage {
                freedomConfig {
                    domainStrategy = when (ipv6Mode) {
                        IPv6Mode.DISABLE -> FreedomConfig.DomainStrategy.USE_IP4
                        IPv6Mode.ONLY -> FreedomConfig.DomainStrategy.USE_IP6
                        else -> FreedomConfig.DomainStrategy.USE_IP
                    }
                }
            }
        })

        outbound.add(outboundHandlerConfig {
            tag = TAG_BLOCK
            proxySettings = typedMessage { BlackholeConfig.getDefaultInstance() }
        })

        inbound.add(inboundHandlerConfig {
            tag = TAG_DNS_IN
            proxySettings = typedMessage {
                dokodemoConfig {
                    address = "1.0.0.1".toIpOrDomain()
                    port = 53
                    networks.addAll(listOf(Network.TCP, Network.UDP))
                }
            }
            receiverSettings = typedMessage {
                receiverConfig {
                    listen = bind.toIpOrDomain()
                    portRange = DataStore.localDNSPort.toPortRange()
                }
            }
        })

        outbound.add(outboundHandlerConfig {
            tag = TAG_DNS_OUT
            proxySettings = typedMessage {
                dnsConfig {
                    server = endpoint {
                        var rds = remoteDns.first()
                        if (rds.contains(":")) {
                            val lPort = rds.substringAfterLast(":")
                            rds = rds.substringBeforeLast(":")
                            if (NumberUtil.isInteger(lPort)) {
                                port = lPort.toInt()
                            }
                        }
                        if (rds.isIpAddress()) {
                            address = rds.toIpOrDomain()
                        } else if (rds.contains("://")) {
                            network = Network.TCP
                            address = rds.substringAfter("://").toIpOrDomain()
                        }
                    }

                }
            }
        })

        for (dns in remoteDns) {
            if (!dns.isIpAddress()) continue
            routing.rules.add(0, RoutingObject.RuleObject().apply {
                type = "field"
                outboundTag = tagProxy
                ip = listOf(dns)
            })
        }

        for (dns in directDNS) {
            if (!dns.isIpAddress()) continue

            routing.rules.add(0, RoutingObject.RuleObject().apply {
                type = "field"
                outboundTag = TAG_DIRECT
                ip = listOf(dns)
            })
        }

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

        if (enableDnsRouting) {
            for (bypassRule in extraRules.filter { it.isBypassRule() }) {
                if (bypassRule.domains.isNotBlank()) {
                    bypassDomain.addAll(bypassRule.domains.split("\n"))
                }
            }
        }

        if (bypassDomain.isNotEmpty()) {
            dns.servers.addAll(directDNS.map {
                DnsObject.StringOrServerObject().apply {
                    valueY = DnsObject.ServerObject().apply {
                        address = it
                        domains = bypassDomain.toList()
                        skipFallback = true
                    }
                }
            })
        }

        if (useFakeDns) {
            dns.servers.add(0, DnsObject.StringOrServerObject().apply {
                valueX = "fakedns"
            })
        }

        routing.rules.add(0, RoutingObject.RuleObject().apply {
            type = "field"
            inboundTag = listOf(TAG_DNS_IN)
            outboundTag = TAG_DNS_OUT
        })

        if (allowAccess) {
            // temp: fix crash
            routing.rules.add(RoutingObject.RuleObject().apply {
                type = "field"
                ip = listOf("255.255.255.255")
                outboundTag = TAG_BLOCK
            })
        }

        if (rootBalancer != null) routing.rules.add(rootBalancer)

        app.add(typedMessage { StatsConfig.getDefaultInstance() })

    }

    val builder = Libcore.newV2RayBuilder(config.toByteArray())
    builder.setDNS(gson.toJson(dns))
    builder.setRouter(gson.toJson(routing))
    builder.close()

    return V2rayBuildResult(
        indexMap, requireWs, wsPort, outboundTags, outboundTagsCurrent, outboundTagsAll, TAG_BYPASS,
        /*it.observatory?.subjectSelector ?:*/
        HashSet(), dumpUid, alerts
    ).apply {
        proto = builder
    }

}
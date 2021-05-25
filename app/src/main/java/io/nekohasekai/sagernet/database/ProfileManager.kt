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

package io.nekohasekai.sagernet.database

import android.database.sqlite.SQLiteCantOpenDatabaseException
import cn.hutool.json.*
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.fixInvalidParams
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.parseShadowsocksR
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan_go.parseTrojanGo
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.*
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.DirectBoot
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.io.IOException
import java.sql.SQLException
import java.util.*
import kotlin.collections.ArrayList


object ProfileManager {

    interface Listener {
        suspend fun onAdd(profile: ProxyEntity)
        suspend fun onUpdated(profileId: Long, trafficStats: TrafficStats)
        suspend fun onUpdated(profile: ProxyEntity)
        suspend fun onRemoved(groupId: Long, profileId: Long)
        suspend fun onCleared(groupId: Long)
        suspend fun reloadProfiles(groupId: Long)
    }

    interface GroupListener {
        suspend fun onAdd(group: ProxyGroup)
        suspend fun onUpdated(group: ProxyGroup)
        suspend fun onRemoved(groupId: Long)

        suspend fun onUpdated(groupId: Long) {}
        suspend fun refreshSubscription(proxyGroup: ProxyGroup) {}
        suspend fun refreshSubscription(
            proxyGroup: ProxyGroup,
            onRefreshStarted: Runnable,
            onRefreshFinished: Runnable,
        ) {
        }
    }

    interface RuleListener {
        suspend fun onAdd(rule: RuleEntity)
        suspend fun onUpdated(rule: RuleEntity)
        suspend fun onRemoved(ruleId: Long)
        suspend fun onCleared()
    }

    private val listeners = ArrayList<Listener>()
    private val groupListeners = ArrayList<GroupListener>()
    private val ruleListeners = ArrayList<RuleListener>()

    suspend fun iterator(what: suspend Listener.() -> Unit) {
        val listeners = synchronized(listeners) {
            listeners.toList()
        }
        for (profileListener in listeners) {
            what(profileListener)
        }
    }

    suspend fun groupIterator(what: suspend GroupListener.() -> Unit) {
        val groupListeners = synchronized(groupListeners) {
            groupListeners.toList()
        }
        for (listener in groupListeners) {
            what(listener)
        }
    }

    suspend fun ruleIterator(what: suspend RuleListener.() -> Unit) {
        val ruleListeners = synchronized(ruleListeners) {
            ruleListeners.toList()
        }
        for (listener in ruleListeners) {
            what(listener)
        }
    }

    fun addListener(listener: Listener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun addListener(listener: GroupListener) {
        synchronized(groupListeners) {
            groupListeners.add(listener)
        }
    }

    fun removeListener(listener: GroupListener) {
        synchronized(groupListeners) {
            groupListeners.remove(listener)
        }
    }

    fun addListener(listener: RuleListener) {
        synchronized(ruleListeners) {
            ruleListeners.add(listener)
        }
    }

    fun removeListener(listener: RuleListener) {
        synchronized(ruleListeners) {
            ruleListeners.remove(listener)
        }
    }

    suspend fun createProfile(groupId: Long, bean: AbstractBean): ProxyEntity {
        val profile = ProxyEntity(groupId = groupId).apply {
            id = 0
            putBean(bean)
            userOrder = SagerDatabase.proxyDao.nextOrder(groupId) ?: 1
        }
        profile.id = SagerDatabase.proxyDao.addProxy(profile)
        iterator { onAdd(profile) }
        return profile
    }

    suspend fun updateProfile(vararg profile: ProxyEntity) {
        SagerDatabase.proxyDao.updateProxy(* profile)
        profile.forEach {
            iterator { onUpdated(it) }
        }
    }

    suspend fun deleteProfile(groupId: Long, profileId: Long) {
        if (SagerDatabase.proxyDao.deleteById(profileId) == 0) return
        if (DataStore.selectedProxy == profileId) {
            if (DataStore.directBootAware) DirectBoot.clean()
            DataStore.selectedProxy = 0L
        }
        iterator { onRemoved(groupId, profileId) }
        if (SagerDatabase.proxyDao.countByGroup(groupId) == 0L) {
            val group = SagerDatabase.groupDao.getById(groupId) ?: return
            if (group.isDefault) {
                val created = createProfile(groupId, SOCKSBean().apply {
                    name = "Local tunnel"
                    initDefaultValues()
                })
                if (DataStore.selectedProxy == 0L) {
                    DataStore.selectedProxy = created.id
                }
            }
        } else {
            rearrange(groupId)
        }
    }

    suspend fun clearGroup(groupId: Long) {
        DataStore.selectedProxy = 0L
        SagerDatabase.proxyDao.deleteAll(groupId)
        if (DataStore.directBootAware) DirectBoot.clean()
        iterator { onCleared(groupId) }
    }

    fun rearrange(groupId: Long) {
        val entities = SagerDatabase.proxyDao.getByGroup(groupId)
        for (index in entities.indices) {
            entities[index].userOrder = (index + 1).toLong()
        }
        SagerDatabase.proxyDao.updateProxy(* entities.toTypedArray())
    }

    fun getProfile(profileId: Long): ProxyEntity? {
        if (profileId == 0L) return null
        return try {
            SagerDatabase.proxyDao.getById(profileId)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            null
        }
    }

    fun getProfiles(profileIds: List<Long>): List<ProxyEntity> {
        if (profileIds.isEmpty()) return listOf()
        return try {
            SagerDatabase.proxyDao.getEntities(profileIds)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            listOf()
        }
    }

    suspend fun postUpdate(profileId: Long) {
        postUpdate(getProfile(profileId) ?: return)
    }

    suspend fun postUpdate(profile: ProxyEntity) {
        iterator { onUpdated(profile) }
    }

    suspend fun postReload(groupId: Long) {
        iterator { reloadProfiles(groupId) }
        groupIterator { onUpdated(groupId) }
    }

    suspend fun postTrafficUpdated(profileId: Long, stats: TrafficStats) {
        iterator { onUpdated(profileId, stats) }
    }

    suspend fun createGroup(group: ProxyGroup): ProxyGroup {
        group.userOrder = SagerDatabase.groupDao.nextOrder() ?: 1
        group.id = SagerDatabase.groupDao.createGroup(group)
        groupIterator { onAdd(group) }
        return group
    }

    suspend fun updateGroup(group: ProxyGroup) {
        SagerDatabase.groupDao.updateGroup(group)
        groupIterator { onUpdated(group) }
    }

    suspend fun deleteGroup(groupId: Long) {
        SagerDatabase.groupDao.deleteById(groupId)
        SagerDatabase.proxyDao.deleteByGroup(groupId)
        groupIterator { onRemoved(groupId) }
    }

    suspend fun deleteGroup(vararg group: ProxyGroup) {
        SagerDatabase.groupDao.deleteGroup(* group)
        SagerDatabase.proxyDao.deleteByGroup(* group.map { it.id }.toLongArray())
        for (proxyGroup in group) {
            groupIterator { onRemoved(proxyGroup.id) }
        }
    }

    suspend fun createRule(rule: RuleEntity, post: Boolean = true): RuleEntity {
        rule.userOrder = SagerDatabase.rulesDao.nextOrder() ?: 1
        rule.id = SagerDatabase.rulesDao.createRule(rule)
        if (post) {
            ruleIterator { onAdd(rule) }
        }
        return rule
    }

    suspend fun updateRule(rule: RuleEntity) {
        SagerDatabase.rulesDao.updateRule(rule)
        ruleIterator { onUpdated(rule) }
    }

    suspend fun deleteRule(ruleId: Long) {
        SagerDatabase.rulesDao.deleteById(ruleId)
        ruleIterator { onRemoved(ruleId) }
    }

    suspend fun deleteRules(rules: List<RuleEntity>) {
        SagerDatabase.rulesDao.deleteRules(rules)
        ruleIterator {
            rules.forEach {
                onRemoved(it.id)
            }
        }
    }

    suspend fun getRules(): List<RuleEntity> {
        var rules = SagerDatabase.rulesDao.allRules()
        if (rules.isEmpty() && !DataStore.rulesFirstCreate) {
            DataStore.rulesFirstCreate = true
            createRule(
                RuleEntity(
                    name = app.getString(R.string.route_opt_block_ads),
                    domains = "geosite:category-ads-all",
                    outbound = -2
                )
            )
            var country = Locale.getDefault().country.lowercase()
            var displayCountry = Locale.getDefault().displayCountry
            if (country !in arrayOf(
                    "ir"
                )
            ) {
                country = Locale.CHINA.country.lowercase()
                displayCountry = Locale.CHINA.displayCountry
                createRule(
                    RuleEntity(
                        name = app.getString(R.string.route_bypass_domain, displayCountry),
                        domains = "geosite:$country",
                        outbound = -1
                    ), false
                )
            }
            createRule(
                RuleEntity(
                    name = app.getString(R.string.route_bypass_ip, displayCountry),
                    ip = "geoip:$country",
                    outbound = -1
                ), false
            )
            rules = SagerDatabase.rulesDao.allRules()
        }
        return rules
    }

    @Suppress("UNCHECKED_CAST")
    fun parseSubscription(text: String): Pair<Int, List<AbstractBean>>? {

        val proxies = ArrayList<AbstractBean>()

        if (text.contains("proxies:")) {

            try {

                // clash
                for (proxy in (Yaml().apply {
                    addTypeDescription(TypeDescription(String::class.java, "str"))
                }.loadAs(
                    text,
                    Map::class.java
                )["proxies"] as? (List<Map<String, Any?>>)
                    ?: error(app.getString(R.string.no_proxies_found_in_file)))) {

                    when (proxy["type"] as String) {
                        "socks5" -> {
                            proxies.add(SOCKSBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                username = proxy["username"] as String?
                                password = proxy["password"] as String?
                                tls = proxy["tls"]?.toString() == "true"
                                sni = proxy["sni"] as String?
//                            udp = proxy["udp"]?.toString() == "true"
                                name = proxy["name"] as String?
                            })
                        }
                        "http" -> {
                            proxies.add(
                                HttpBean().apply {
                                    serverAddress = proxy["server"] as String
                                    serverPort = proxy["port"].toString().toInt()
                                    username = proxy["username"] as String?
                                    password = proxy["password"] as String?
                                    tls = proxy["tls"]?.toString() == "true"
                                    sni = proxy["sni"] as String?
                                    name = proxy["name"] as String?
                                }
                            )
                        }
                        "ss" -> {
                            var pluginStr = ""
                            if (proxy.contains("plugin")) {
                                val opts = PluginOptions()
                                opts.id = proxy["plugin"] as String
                                opts.putAll(proxy["plugin-opts"] as Map<String, String?>)
                                pluginStr = opts.toString(false)
                            }
                            proxies.add(ShadowsocksBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                password = proxy["password"] as String
                                method = proxy["cipher"] as String
                                plugin = pluginStr
                                name = proxy["name"] as String?

                                fixInvalidParams()
                            })
                        }
                        "vmess" -> {
                            val bean = VMessBean()
                            for (opt in proxy) {
                                when (opt.key) {
                                    "name" -> bean.name = opt.value as String
                                    "server" -> bean.serverAddress = opt.value as String
                                    "port" -> bean.serverPort = opt.value.toString().toInt()
                                    "uuid" -> bean.uuid = opt.value as String
                                    "alterId" -> bean.alterId = opt.value.toString().toInt()
                                    "cipher" -> bean.security = opt.value as String
                                    "network" -> bean.type = opt.value as String
                                    "tls" -> bean.security =
                                        if (opt.value?.toString() == "true") "tls" else ""
                                    "ws-path" -> bean.path = opt.value as String
                                    "ws-headers" -> for (wsOpt in (opt.value as Map<String, Any>)) {
                                        when (wsOpt.key.lowercase()) {
                                            "host" -> bean.host = wsOpt.value as String
                                        }
                                    }
                                    "servername" -> bean.host = opt.value as String
                                    "h2-opts" -> for (h2Opt in (opt.value as Map<String, Any>)) {
                                        when (h2Opt.key.lowercase()) {
                                            "host" -> bean.host =
                                                (h2Opt.value as List<String>).first()
                                            "path" -> bean.path = h2Opt.value as String
                                        }
                                    }
                                    "http-opts" -> for (httpOpt in (opt.value as Map<String, Any>)) {
                                        when (httpOpt.key.lowercase()) {
                                            "path" -> bean.path =
                                                (httpOpt.value as List<String>).first()
                                        }
                                    }
                                    "grpc-opts" -> for (grpcOpt in (opt.value as Map<String, Any>)) {
                                        when (grpcOpt.key.lowercase()) {
                                            "grpc-service-name" -> bean.path =
                                                grpcOpt.value as String
                                        }
                                    }
                                }
                            }
                            proxies.add(bean)
                        }
                        "trojan" -> {
                            val bean = TrojanBean()
                            for (opt in proxy) {
                                when (opt.key) {
                                    "name" -> bean.name = opt.value as String?
                                    "server" -> bean.serverAddress = opt.value as String
                                    "port" -> bean.serverPort = opt.value.toString().toInt()
                                    "password" -> bean.password = opt.value as String
                                    "sni" -> bean.sni = opt.value as String?
                                }
                            }
                            proxies.add(bean)
                        }

                        "ssr" -> {
                            val entity = ShadowsocksRBean()
                            for (opt in proxy) {
                                when (opt.key) {
                                    "name" -> entity.name = opt.value as String
                                    "server" -> entity.serverAddress = opt.value as String
                                    "port" -> entity.serverPort = opt.value.toString().toInt()
                                    "cipher" -> entity.method = opt.value as String
                                    "password" -> entity.password = opt.value as String
                                    "obfs" -> entity.obfs = opt.value as String
                                    "protocol" -> entity.protocol = opt.value as String
                                    "obfs-param" -> entity.obfsParam = opt.value as String
                                    "protocol-param" -> entity.protocolParam =
                                        opt.value as String
                                }
                            }
                            proxies.add(entity)
                        }
                    }
                }
                proxies.forEach { it.initDefaultValues() }
                return 1 to proxies
            } catch (e: YAMLException) {
                Logs.w(e)
            }
        }

        try {
            val json = JSONUtil.parse(text)
            return 2 to parseJSON(json)
        } catch (ignored: JSONException) {
        }

        try {
            return parseProxies(text.decodeBase64UrlSafe(), 3).takeIf { it.second.isNotEmpty() }
                ?: error("Not found")
        } catch (e: Exception) {
            Logs.w(e)
        }

        try {
            return parseProxies(text).takeIf { it.second.isNotEmpty() } ?: error("Not found")
        } catch (ignored: Exception) {
        }

        return null
    }

    fun parseJSON(json: JSON): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        if (json is JSONObject) {
            when {
                json.containsKey("protocol_param") -> {
                    return listOf(json.parseShadowsocksR())
                }
                json.containsKey("method") -> {
                    return listOf(json.parseShadowsocks())
                }
                json.containsKey("protocol") -> {
                    val v2rayConfig = gson.fromJson(
                        json.toString(),
                        OutboundObject::class.java
                    ).apply { init() }
                    return parseOutbound(v2rayConfig)
                }
                json.containsKey("outbound") -> {
                    val v2rayConfig = gson.fromJson(
                        json.getJSONObject("outbound").toString(),
                        OutboundObject::class.java
                    ).apply { init() }
                    return parseOutbound(v2rayConfig)
                }
                json.containsKey("outbounds") -> {
                    val fakedns = json["fakedns"]
                    if (fakedns is JSONObject) {
                        json["fakedns"] = JSONArray().apply {
                            add(fakedns)
                        }
                    }

                    try {
                        gson.fromJson(
                            json.toString(),
                            V2RayConfig::class.java
                        ).apply { init() }
                    } catch (e: Exception) {
                        Logs.w(e)
                        json.getJSONArray("outbounds").toList(JSONObject::class.java).forEach {
                            val v2rayConfig = gson
                                .fromJson(it.toString(), OutboundObject::class.java)
                                .apply { init() }

                            proxies.addAll(parseOutbound(v2rayConfig))
                        }
                        null
                    }?.outbounds?.forEach {
                        proxies.addAll(parseOutbound(it))
                    }
                }
                json.containsKey("remote_addr") -> {
                    return listOf(json.parseTrojanGo())
                }
                else -> json.forEach { _, it ->
                    if (it is JSON) {
                        proxies.addAll(parseJSON(it))
                    }
                }
            }
        } else {
            json as JSONArray
            json.forEach {
                if (it is JSON) {
                    proxies.addAll(parseJSON(it))
                }
            }
        }

        return proxies
    }

    fun parseOutbound(outboundObject: OutboundObject): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        with(outboundObject) {
            when (protocol) {
                "http" -> {
                    val httpBean = HttpBean().applyDefaultValues()
                    streamSettings?.apply {
                        when (security) {
                            "tls" -> {
                                httpBean.tls = true
                                tlsSettings?.serverName?.also {
                                    httpBean.sni = it
                                }
                            }
                        }
                    }
                    (settings.value as? HTTPOutboundConfigurationObject)?.servers?.forEach {
                        val httpBeanNext = httpBean.clone().apply {
                            serverAddress = it.address
                            serverPort = it.port
                        }
                        if (it.users.isNullOrEmpty()) {
                            proxies.add(httpBeanNext)
                        } else for (user in it.users) proxies.add(httpBeanNext.clone().apply {
                            username = user.user
                            password = user.pass
                            name = displayName() + " - $username"
                        })
                    }
                }
                "socks" -> {
                    val socksBean = SOCKSBean().applyDefaultValues()
                    streamSettings?.apply {
                        when (security) {
                            "tls" -> {
                                socksBean.tls = true
                                tlsSettings?.serverName?.also {
                                    socksBean.sni = it
                                }
                            }
                        }
                    }
                    (settings.value as? SocksOutboundConfigurationObject)?.servers?.forEach {
                        val socksBeanNext = socksBean.clone().apply {
                            serverAddress = it.address
                            serverPort = it.port
                        }
                        if (it.users.isNullOrEmpty()) {
                            proxies.add(socksBeanNext)
                        } else for (user in it.users) proxies.add(socksBeanNext.clone().apply {
                            username = user.user
                            password = user.pass
                            name = displayName() + " - $username"
                        })
                    }
                }
                "vmess", "vless" -> {
                    val v2rayBean =
                        (if (protocol == "vmess") VMessBean() else VLESSBean()).applyDefaultValues()
                    streamSettings?.apply {
                        v2rayBean.security = security ?: v2rayBean.security
                        when (security) {
                            "tls" -> {
                                tlsSettings?.apply {
                                    serverName?.also {
                                        v2rayBean.sni = it
                                    }
                                    alpn?.also {
                                        v2rayBean.alpn = it.joinToString(",")
                                    }
                                }
                            }
                            "xtls" -> {
                                xtlsSettings?.apply {
                                    serverName?.also {
                                        v2rayBean.sni = it
                                    }
                                    alpn?.also {
                                        v2rayBean.alpn = it.joinToString(",")
                                    }
                                }
                            }
                        }
                        v2rayBean.type = network ?: v2rayBean.type
                        when (network) {
                            "tcp" -> {
                                tcpSettings?.header?.apply {
                                    when (type) {
                                        "http" -> {
                                            v2rayBean.headerType = "http"
                                            request?.apply {
                                                path?.also {
                                                    v2rayBean.path = it.joinToString(",")
                                                }
                                                headers?.forEach { (key, value) ->
                                                    when (key.lowercase()) {
                                                        "host" -> {
                                                            when {
                                                                value.valueX != null -> {
                                                                    v2rayBean.host = value.valueX
                                                                }
                                                                value.valueY != null -> {
                                                                    v2rayBean.host =
                                                                        value.valueY.joinToString(",")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "kcp" -> {
                                kcpSettings?.apply {
                                    header?.type?.also {
                                        v2rayBean.headerType = it
                                    }
                                    seed?.also {
                                        v2rayBean.mKcpSeed = it
                                    }
                                }
                            }
                            "ws" -> {
                                wsSettings?.apply {
                                    headers?.forEach { (key, value) ->
                                        when (key.lowercase()) {
                                            "host" -> {
                                                v2rayBean.host = value
                                            }
                                        }
                                    }

                                    path?.also {
                                        v2rayBean.path = it
                                        val pathUrl = "http://localhost$path".toHttpUrlOrNull()
                                        if (pathUrl != null) {
                                            pathUrl.queryParameter("ed")?.let {
                                                runCatching {
                                                    v2rayBean.wsMaxEarlyData = it.toInt()
                                                }
                                            }
                                        }
                                    }

                                    maxEarlyData?.also {
                                        v2rayBean.wsMaxEarlyData = it
                                    }
                                }
                            }
                            "http", "h2" -> {
                                v2rayBean.type = "h2"

                                httpSettings?.apply {
                                    host?.also {
                                        v2rayBean.host = it.joinToString(",")
                                    }
                                    path?.also {
                                        v2rayBean.path = it
                                    }
                                }
                            }
                            "quic" -> {
                                quicSettings?.apply {
                                    security?.also {
                                        v2rayBean.quicSecurity = it
                                    }
                                    key?.also {
                                        v2rayBean.quicKey = it
                                    }
                                    header?.type?.also {
                                        v2rayBean.headerType = it
                                    }
                                }
                            }
                            "grpc" -> {
                                grpcSettings?.serviceName?.also {
                                    v2rayBean.grpcServiceName = it
                                }
                            }
                        }
                    }
                    if (protocol == "vmess") {
                        v2rayBean as VMessBean
                        (settings.value as? VMessOutboundConfigurationObject)?.vnext?.forEach {
                            val vmessBean = v2rayBean.clone().apply {
                                serverAddress = it.address
                                serverPort = it.port
                            }
                            for (user in it.users) {
                                proxies.add(vmessBean.clone().apply {
                                    uuid = user.id
                                    encryption = user.security
                                    alterId = user.alterId
                                    name = displayName() + " - ${user.security} - ${user.id}"
                                })
                            }
                        }
                    } else {
                        v2rayBean as VLESSBean
                        (settings.value as? VLESSOutboundConfigurationObject)?.vnext?.forEach {
                            val vlessBean = v2rayBean.clone().apply {
                                serverAddress = it.address
                                serverPort = it.port
                            }
                            for (user in it.users) {
                                proxies.add(vlessBean.clone().apply {
                                    uuid = user.id
                                    encryption = user.encryption
                                    name = displayName() + " - ${user.id}"
                                    if (!user.flow.isNullOrBlank()) {
                                        flow = user.flow
                                    }
                                })
                            }
                        }
                    }
                }
                "shadowsocks" -> (settings.value as? ShadowsocksOutboundConfigurationObject)?.servers?.forEach {
                    proxies.add(ShadowsocksBean().applyDefaultValues().apply {
                        serverAddress = it.address
                        serverPort = it.port
                        method = it.method
                        password = it.password
                        plugin = ""
                    })
                }
                "trojan" -> {
                    val trojanBean = TrojanBean().applyDefaultValues()

                    streamSettings?.apply {
                        trojanBean.security = security ?: trojanBean.security
                        when (security) {
                            "tls" -> {
                                tlsSettings?.apply {
                                    serverName?.also {
                                        trojanBean.sni = it
                                    }
                                    alpn?.also {
                                        trojanBean.alpn = it.joinToString(",")
                                    }
                                }
                            }
                            "xtls" -> {
                                xtlsSettings?.apply {
                                    serverName?.also {
                                        trojanBean.sni = it
                                    }
                                    alpn?.also {
                                        trojanBean.alpn = it.joinToString(",")
                                    }
                                }
                            }
                        }

                        (settings.value as? TrojanOutboundConfigurationObject)?.servers?.forEach {
                            proxies.add(trojanBean.clone().apply {
                                serverAddress = it.address
                                serverPort = it.port
                                password = it.password
                                it.flow?.also {
                                    flow = it
                                }
                            })
                        }
                    }
                }
            }
            Unit
        }

        return proxies
    }

}

















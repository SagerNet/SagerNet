package io.nekohasekai.sagernet.database

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.os.SystemClock
import android.util.Base64
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.parseProxies
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.yaml.snakeyaml.Yaml
import java.io.IOException
import java.lang.ref.WeakReference
import java.sql.SQLException
import java.util.*

object ProfileManager {

    private val listeners = LinkedList<WeakReference<Listener>>()
    private val groupListeners = LinkedList<WeakReference<GroupListener>>()
    private suspend fun iterator(what: Listener.() -> Unit) {
        onMainDispatcher {
            synchronized(listeners) {
                val iterator = listeners.iterator()
                while (iterator.hasNext()) {
                    val listener = iterator.next().get()
                    if (listener == null) {
                        iterator.remove()
                        continue
                    }
                    what(listener)
                }
            }
        }
    }

    private suspend fun groupIterator(what: GroupListener.() -> Unit) {
        onMainDispatcher {
            synchronized(groupListeners) {
                val iterator = groupListeners.iterator()
                while (iterator.hasNext()) {
                    val listener = iterator.next().get()
                    if (listener == null) {
                        iterator.remove()
                        continue
                    }
                    what(listener)
                }
            }
        }
    }

    fun addListener(listener: Listener) {
        synchronized(listeners) {
            listeners.add(WeakReference(listener))
        }
    }

    fun addListener(listener: GroupListener) {
        synchronized(groupListeners) {
            groupListeners.add(WeakReference(listener))
        }
    }

    interface Listener {
        fun onAdd(profile: ProxyEntity)
        fun onUpdated(profile: ProxyEntity)
        fun onRemoved(groupId: Long, profileId: Long)
        fun onCleared(groupId: Long)
        fun reloadProfiles(groupId: Long)
    }

    interface GroupListener {
        fun onAdd(group: ProxyGroup)
        fun onAddFinish(size: Int)
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

    suspend fun updateProfile(profile: ProxyEntity) {
        SagerDatabase.proxyDao.updateProxy(profile)
        iterator { onUpdated(profile) }
    }

    suspend fun deleteProfile(groupId: Long, profileId: Long) {
        check(SagerDatabase.proxyDao.deleteById(profileId) > 0)
        if (DataStore.selectedProxy == profileId) {
            DataStore.selectedProxy = 0L
        }
        iterator { onRemoved(groupId, profileId) }
        if (SagerDatabase.proxyDao.countByGroup(groupId) == 0L) {
            val group = SagerDatabase.groupDao.getById(groupId) ?: return
            if (group.isDefault) {
                val created = createProfile(groupId, SOCKSBean.DEFAULT_BEAN.clone().apply {
                    name = "Local tunnel"
                })
                if (DataStore.selectedProxy == 0L) {
                    DataStore.selectedProxy = created.id
                }
            }
        } else {
            rearrange(groupId)
        }
    }

    suspend fun clear(groupId: Long) {
        SagerDatabase.proxyDao.deleteAll(groupId)
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

    suspend fun postUpdate(profileId: Long) {
        val profile = getProfile(profileId) ?: return
        iterator { onUpdated(profile) }
    }

    suspend fun postTrafficUpdated(profileId: Long, stats: TrafficStats) {
        val profile = getProfile(profileId) ?: return
        profile.stats = stats
        iterator { onUpdated(profile) }
    }

    suspend fun createGroup(response: Response) {
        val proxies = parseSubscription((response.body ?: error("Empty response")).string())
        if (proxies.isEmpty()) error(SagerNet.application.getString(R.string.no_proxies_found))

        val newGroup = ProxyGroup(
            name = "New group",
            isSubscription = true,
            subscriptionLinks = mutableListOf(response.request.url.toString()),
            lastUpdate = SystemClock.elapsedRealtimeNanos()
        )

        newGroup.id = SagerDatabase.groupDao.createGroup(newGroup)

        groupIterator { onAdd(newGroup) }

        for (proxy in proxies) {
            createProfile(newGroup.id, proxy)
        }

        groupIterator { onAddFinish(proxies.size) }
    }

    fun parseSubscription(text: String, tryDecode: Boolean = true): List<AbstractBean> {
        if (tryDecode) {
            try {
                return parseSubscription(String(Base64.decode(text, Base64.NO_PADDING)), false)
            } catch (ignored: Exception) {
            }
        }

        val proxies = LinkedList<AbstractBean>()
        try {
            // sip008
            val ssArray = JSONArray(text)
            try {
                for (index in 0 until ssArray.length()) {
                    proxies.add(parseShadowsocks(ssArray.getJSONObject(index)))
                }
            } catch (e: Exception) {
                throw  e
            }
            return proxies
        } catch (ignored: JSONException) {
        }

        if (text.contains("proxies:\n")) {

            // clash

            for (proxy in (Yaml().loadAs(text,
                Map::class.java)["proxies"] as List<Map<String, Any?>>)) {
                val type = proxy["type"] as String
                when (type) {
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
                            serverPort = proxy["port"] as Int
                            password = proxy["password"] as String
                            method = proxy["cipher"] as String
                            plugin = pluginStr
                            name = proxy["name"] as String? ?: ""
                        })
                        /*"vmess" -> {
                            val opts = AngConfig.VmessBean()
                            for (opt in proxy) {
                                when (opt.key) {
                                    "name" -> opts.remarks = opt.value as String
                                    "server" -> opts.address = opt.value as String
                                    "port" -> opts.port = opt.value as Int
                                    "uuid" -> opts.id = opt.value as String
                                    "alterId" -> opts.alterId = opt.value as Int
                                    "cipher" -> opts.security = opt.value as String
                                    "network" -> opts.network = opt.value as String
                                    "tls" -> opts.streamSecurity = if (opt.value?.toString() == "true") "tls" else opts.streamSecurity
                                    "ws-path" -> opts.path = opt.value as String
                                    "servername" -> opts.requestHost = opt.value as String
                                    "h2-opts" -> for (h2Opt in (opt.value as Map<String, Any>)) {
                                        when (h2Opt.key) {
                                            "host" -> opts.requestHost = (h2Opt.value as List<String>).first()
                                            "path" -> opts.path = h2Opt.value as String
                                        }
                                    }
                                    "http-opts" -> for (httpOpt in (opt.value as Map<String, Any>)) {
                                        when (httpOpt.key) {
                                            "path" -> opts.path = (httpOpt.value as List<String>).first()
                                        }
                                    }
                                }
                            }
                            proxies.add(opts.toString())
                        }
                        "trojan" -> {
                            val opts = AngConfig.VmessBean()
                            opts.configType = V2RayConfig.EConfigType.Trojan
                            for (opt in proxy) {
                                when (opt.key) {
                                    "name" -> opts.remarks = opt.value as String
                                    "server" -> opts.address = opt.value as String
                                    "port" -> opts.port = opt.value as Int
                                    "password" -> opts.id = opt.value as String
                                    "sni" -> opts.requestHost = opt.value as String
                                }
                            }
                            proxies.add(opts.toString())
                        }
                        "ssr" -> {
                            val opts = ShadowsocksRLoader.Bean()
                            for (opt in proxy) {
                                when (opt.key) {
                                    "name" -> opts.remarks = opt.value as String
                                    "server" -> opts.host = opt.value as String
                                    "port" -> opts.remotePort = opt.value as Int
                                    "cipher" -> opts.method = opt.value as String
                                    "password" -> opts.password = opt.value as String
                                    "obfs" -> opts.obfs = opt.value as String
                                    "protocol" -> opts.protocol = opt.value as String
                                    "obfs-param" -> opts.obfs_param = opt.value as String
                                    "protocol-param" -> opts.protocol_param = opt.value as String
                                }
                            }
                            proxies.add(opts.toString())
                        }*/
                    }
                }
                return proxies
            }
        }

        return parseProxies(text)

    }

}

















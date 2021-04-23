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
import android.util.Base64
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.utils.DirectBoot
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.yaml.snakeyaml.Yaml
import java.io.IOException
import java.lang.ref.WeakReference
import java.sql.SQLException
import java.util.*

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


    private val listeners = LinkedList<WeakReference<Listener>>()
    private val groupListeners = LinkedList<WeakReference<GroupListener>>()

    suspend fun iterator(what: suspend Listener.() -> Unit) {
        val listeners = synchronized(listeners) {
            listeners
        }
        for (profileListener in listeners) {
            val listener = profileListener.get()
            if (listener == null) {
                this.listeners.remove(profileListener)
                return
            }
            what(listener)
        }
    }

    suspend fun groupIterator(what: suspend GroupListener.() -> Unit) {
        val groupListeners = synchronized(groupListeners) {
            groupListeners
        }
        for (groupListener in groupListeners) {
            val listener = groupListener.get()
            if (listener == null) {
                this.groupListeners.remove(groupListener)
                return
            }
            what(listener)
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
        check(SagerDatabase.proxyDao.deleteById(profileId) > 0)
        if (DataStore.selectedProxy == profileId) {
            if (DataStore.directBootAware) DirectBoot.clean()
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
        groupIterator { onRemoved(groupId) }
    }

    suspend fun deleteGroup(vararg group: ProxyGroup) {
        SagerDatabase.groupDao.deleteGroup(* group)
        for (proxyGroup in group) {
            groupIterator { onRemoved(proxyGroup.id) }
        }
    }


    suspend fun createGroup(response: Response) {
        /* val proxies =
         if (proxies.isEmpty()) error(SagerNet.application.getString(R.string.no_proxies_found))

         val newGroup = ProxyGroup(
             name = "New group",
             isSubscription = true,
             subscriptionLinks = mutableListOf(response.request.url.toString()),
             lastUpdate = SystemClock.elapsedRealtimeNanos(),
         )

         newGroup.id = SagerDatabase.groupDao.createGroup(newGroup)

         groupIterator { onAdd(newGroup) }

         for (proxy in proxies) {
             createProfile(newGroup.id, proxy)
         }

         groupIterator { onAddFinish(proxies.size) }*/
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

        val results = parseProxies(text)
        if (results.isEmpty()) error(app.getString(R.string.no_proxies_found))
        return results

    }

}

















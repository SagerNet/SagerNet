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
import io.nekohasekai.sagernet.fmt.shadowsocks.fixInvalidParams
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.utils.DirectBoot
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
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


    private val listeners = ArrayList<Listener>()
    private val groupListeners = ArrayList<GroupListener>()

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


    @Suppress("UNCHECKED_CAST")
    fun parseSubscription(text: String, tryDecode: Boolean = true): Pair<Int, List<AbstractBean>> {
        if (tryDecode) {
            try {
                return parseSubscription(String(Base64.decode(text, Base64.NO_PADDING)), false)
            } catch (ignored: Exception) {
            }
        }

        val proxies = LinkedList<AbstractBean>()
        try {
            // sip008
            val ssArray = try {
                JSONArray(text)
            } catch (e: JSONException) {
                JSONObject(text).getJSONArray("servers")
            } catch (e: JSONException) {
                throw e
            }
            try {
                for (index in 0 until ssArray.length()) {
                    proxies.add(parseShadowsocks(ssArray.getJSONObject(index)))
                }
            } catch (e: Exception) {
                throw  e
            }
            return 0 to proxies
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
                            serverPort = proxy["port"].toString().toInt()
                            password = proxy["password"] as String
                            method = proxy["cipher"] as String
                            plugin = pluginStr
                            name = proxy["name"] as String? ?: ""

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
                                "network" -> bean.network = opt.value as String
                                "tls" -> bean.tls = opt.value?.toString() == "true"
                                "ws-path" -> bean.path = opt.value as String
                                "servername" -> bean.requestHost = opt.value as String
                                "h2-opts" -> for (h2Opt in (opt.value as Map<String, Any>)) {
                                    when (h2Opt.key) {
                                        "host" -> bean.requestHost =
                                            (h2Opt.value as List<String>).first()
                                        "path" -> bean.path = h2Opt.value as String
                                    }
                                }
                                "http-opts" -> for (httpOpt in (opt.value as Map<String, Any>)) {
                                    when (httpOpt.key) {
                                        "path" -> bean.path =
                                            (httpOpt.value as List<String>).first()
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
                                "name" -> bean.name = opt.value as String
                                "server" -> bean.serverAddress = opt.value as String
                                "port" -> bean.serverPort = opt.value.toString().toInt()
                                "password" -> bean.password = opt.value as String
                                "sni" -> bean.sni = opt.value as String
                            }
                        }
                        bean.initDefaultValues()
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
            return 1 to proxies
        }

        val results = parseProxies(text)
        if (results.isEmpty()) error(app.getString(R.string.no_proxies_found))
        return 2 to results

    }

}

















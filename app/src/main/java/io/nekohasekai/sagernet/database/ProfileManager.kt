package io.nekohasekai.sagernet.database

import android.database.sqlite.SQLiteCantOpenDatabaseException
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import java.io.IOException
import java.lang.ref.WeakReference
import java.sql.SQLException
import java.util.*

object ProfileManager {

    private val listeners = LinkedList<WeakReference<Listener>>()

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

    fun addListener(listener: Listener) {
        synchronized(listeners) {
            listeners.add(WeakReference(listener))
        }
    }

    interface Listener {
        fun onAdd(profile: ProxyEntity)
        fun onUpdated(profile: ProxyEntity)
        fun onRemoved(groupId: Long, profileId: Long)
        fun onCleared(groupId: Long)
        fun reloadProfiles(groupId: Long)
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
        return try {
            SagerDatabase.proxyDao.getById(profileId)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            null
        }
    }

}
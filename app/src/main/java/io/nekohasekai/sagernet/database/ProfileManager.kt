package io.nekohasekai.sagernet.database

import android.database.sqlite.SQLiteCantOpenDatabaseException
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import java.io.IOException
import java.lang.ref.WeakReference
import java.sql.SQLException
import java.util.*

object ProfileManager {

    private val listeners = LinkedList<WeakReference<Listener>>()
    private fun iterator(what: Listener.() -> Unit) {
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

    fun createProfile(groupId: Long, bean: AbstractBean): ProxyEntity {
        val profile = ProxyEntity(groupId = groupId).apply {
            id = 0
            putBean(bean)
            userOrder = SagerDatabase.proxyDao.nextOrder(groupId) ?: 1
        }
        profile.id = SagerDatabase.proxyDao.addProxy(profile)
        iterator { onAdd(profile) }
        return profile
    }

    fun updateProfile(profile: ProxyEntity) {
        SagerDatabase.proxyDao.updateProxy(profile)
    }

    fun deleteProfile(groupId: Long, profileId: Long) {
        check(SagerDatabase.proxyDao.deleteById(profileId) > 0)
        iterator { onRemoved(groupId, profileId) }
        rearrange(groupId)
    }

    fun clear(groupId: Long) {
        SagerDatabase.proxyDao.deleteAll(groupId)
        iterator { onCleared(groupId) }
    }

    fun rearrange(groupId: Long) {
        runOnDefaultDispatcher {
            val entities = SagerDatabase.proxyDao.getByGroup(groupId)
            for (index in entities.indices) {
                entities[index].userOrder = (index + 1).toLong()
            }
            SagerDatabase.proxyDao.updateProxy(* entities.toTypedArray())
        }
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
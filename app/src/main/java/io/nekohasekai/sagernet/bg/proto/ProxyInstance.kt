/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.bg.proto

//import io.nekohasekai.sagernet.BuildConfig
//import io.nekohasekai.sagernet.bg.test.DebugInstance
import cn.hutool.core.util.NumberUtil
import com.v2ray.core.app.observatory.OutboundStatus
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.DirectBoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import libcore.Libcore
import java.io.IOException
import java.util.*
import kotlin.concurrent.timerTask

class ProxyInstance(profile: ProxyEntity, val service: BaseService.Interface) : V2RayInstance(
    profile
) {

    lateinit var observatoryJob: Job

    override fun init() {
        if (service is VpnService) {
            Libcore.setProtector { service.protect(it) }
            Libcore.setIPv6Mode(DataStore.ipv6Mode)
        } else {
            Libcore.setProtector { true }
        }

        super.init()

        Logs.d(config.config)
        pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override fun launch() {
        super.launch()

        if (config.observatoryTags.isNotEmpty()) {
            v2rayPoint.setStatusUpdateListener(::sendObservatoryResult)
            observatoryJob = runOnDefaultDispatcher {
                sendInitStatuses()
            }
        }

        if (DataStore.allowAccess) {
            val api = ApiInstance()
            try {
                api.launch()
                externalInstances[11451] = api
            } catch (e: Exception) {
                Logs.w("Failed to start api server", e)
            }
        }

        /* if (BuildConfig.DEBUG && DataStore.enableLog) {
             externalInstances[9999] = DebugInstance().apply {
                 launch()
             }
         }*/

        SagerNet.started = true
    }

    fun sendInitStatuses() {
        val time = (System.currentTimeMillis() / 1000) - 300
        for (observatoryTag in config.observatoryTags) {
            val profileId = observatoryTag.substringAfter("global-")
            if (NumberUtil.isLong(profileId)) {
                val id = profileId.toLong()
                val profile = when {
                    id == profile.id -> profile
                    statsOutbounds.containsKey(id) -> statsOutbounds[id]!!.proxyEntity
                    else -> SagerDatabase.proxyDao.getById(id)
                } ?: continue

                if (profile.status > 0) v2rayPoint.updateStatus(
                    OutboundStatus.newBuilder()
                        .setOutboundTag(observatoryTag)
                        .setAlive(profile.status == 1)
                        .setDelay(profile.ping.toLong())
                        .setLastErrorReason(profile.error ?: "")
                        .setLastTryTime(time)
                        .setLastSeenTime(time)
                        .build()
                        .toByteArray()
                )
            }
        }
    }

    val updateTimer by lazy { Timer("Observatory Timer") }
    val updateTasks by lazy { hashMapOf<Long, TimerTask>() }

    fun sendObservatoryResult(statusPb: ByteArray?) {
        if (statusPb == null || statusPb.isEmpty()) {
            return
        }
        val status = OutboundStatus.parseFrom(statusPb)
        val profileId = status.outboundTag.substringAfter("global-")
        if (NumberUtil.isLong(profileId)) {
            val id = profileId.toLong()
            val profile = when {
                id == profile.id -> profile
                statsOutbounds.containsKey(id) -> statsOutbounds[id]!!.proxyEntity
                else -> {
                    SagerDatabase.proxyDao.getById(id)
                }
            }

            if (profile != null) {
                val newStatus = if (status.alive) 1 else 3
                val newDelay = status.delay.toInt()
                val newErrorReason = status.lastErrorReason

                if (profile.status != newStatus || profile.ping != newDelay || profile.error != newErrorReason) {
                    profile.status = newStatus
                    profile.ping = newDelay
                    profile.error = newErrorReason

                    SagerDatabase.proxyDao.updateProxy(profile)

                    Logs.d("Send result for #$profileId ${profile.displayName()}")

                    val groupId = profile.groupId
                    updateTasks.put(groupId, timerTask {
                        synchronized(this@ProxyInstance) {
                            if (updateTasks[groupId] == this) {
                                service.data.binder.broadcast {
                                    it.observatoryResultsUpdated(profile.groupId)
                                }
                                updateTasks.remove(profile.groupId)
                            }
                        }
                    }.also {
                        updateTimer.schedule(it, 2333L)
                    })?.cancel()
                }
            } else {
                Logs.d("Profile with id #$profileId not found")
            }
        } else {
            Logs.d("Persist skipped on outbound ${status.outboundTag}")
        }
    }

    override fun close() {
        SagerNet.started = false

        persistStats()
        super.close()

        if (::observatoryJob.isInitialized) observatoryJob.cancel()
    }

    // ------------- stats -------------

    private suspend fun queryStats(tag: String, direct: String): Long {
        return v2rayPoint.queryStats(tag, direct)
    }

    private val currentTags by lazy {
        mapOf(* config.outboundTagsCurrent.map {
            it to config.outboundTagsAll[it]
        }.toTypedArray())
    }

    private val statsTags by lazy {
        mapOf(*  config.outboundTags.toMutableList().apply {
            removeAll(config.outboundTagsCurrent)
        }.map {
            it to config.outboundTagsAll[it]
        }.toTypedArray())
    }

    private val interTags by lazy {
        config.outboundTagsAll.filterKeys { !config.outboundTags.contains(it) }
    }

    class OutboundStats(
        val proxyEntity: ProxyEntity, var uplinkTotal: Long = 0L, var downlinkTotal: Long = 0L
    )

    private val statsOutbounds = hashMapOf<Long, OutboundStats>()
    private fun registerStats(
        proxyEntity: ProxyEntity, uplink: Long? = null, downlink: Long? = null
    ) {
        if (proxyEntity.id == outboundStats.proxyEntity.id) return
        val stats = statsOutbounds.getOrPut(proxyEntity.id) {
            OutboundStats(proxyEntity)
        }
        if (uplink != null) {
            stats.uplinkTotal += uplink
        }
        if (downlink != null) {
            stats.downlinkTotal += downlink
        }
    }

    var uplinkProxy = 0L
    var downlinkProxy = 0L
    var uplinkTotalDirect = 0L
    var downlinkTotalDirect = 0L

    private val outboundStats = OutboundStats(profile)
    suspend fun outboundStats(): Pair<OutboundStats, HashMap<Long, OutboundStats>> {
        if (!isInitialized()) return outboundStats to statsOutbounds
        uplinkProxy = 0L
        downlinkProxy = 0L

        val currentUpLink = currentTags.map { (tag, profile) ->
            queryStats(tag, "uplink").apply {
                profile?.also {
                    registerStats(
                        it, uplink = this
                    )
                }
            }
        }
        val currentDownLink = currentTags.map { (tag, profile) ->
            queryStats(tag, "downlink").apply {
                profile?.also {
                    registerStats(it, downlink = this)
                }
            }
        }
        uplinkProxy += currentUpLink.fold(0L) { acc, l -> acc + l }
        downlinkProxy += currentDownLink.fold(0L) { acc, l -> acc + l }

        outboundStats.uplinkTotal += uplinkProxy
        outboundStats.downlinkTotal += downlinkProxy

        if (statsTags.isNotEmpty()) {
            uplinkProxy += statsTags.map { (tag, profile) ->
                queryStats(tag, "uplink").apply {
                    profile?.also {
                        registerStats(it, uplink = this)
                    }
                }
            }.fold(0L) { acc, l -> acc + l }
            downlinkProxy += statsTags.map { (tag, profile) ->
                queryStats(tag, "downlink").apply {
                    profile?.also {
                        registerStats(it, downlink = this)
                    }
                }
            }.fold(0L) { acc, l -> acc + l }
        }

        if (interTags.isNotEmpty()) {
            interTags.map { (tag, profile) ->
                queryStats(tag, "uplink").also { registerStats(profile, uplink = it) }
            }
            interTags.map { (tag, profile) ->
                queryStats(tag, "downlink").also {
                    registerStats(profile, downlink = it)
                }
            }
        }

        return outboundStats to statsOutbounds
    }

    suspend fun bypassStats(direct: String): Long {
        if (!isInitialized()) return 0L
        return queryStats(config.bypassTag, direct)
    }

    suspend fun uplinkDirect() = bypassStats("uplink").also {
        uplinkTotalDirect += it
    }

    suspend fun downlinkDirect() = bypassStats("downlink").also {
        downlinkTotalDirect += it
    }

    fun persistStats() {
        runBlocking {
            try {
                outboundStats()

                val toUpdate = mutableListOf<ProxyEntity>()
                if (outboundStats.uplinkTotal + outboundStats.downlinkTotal != 0L) {
                    profile.tx += outboundStats.uplinkTotal
                    profile.rx += outboundStats.downlinkTotal
                    toUpdate.add(profile)
                }

                statsOutbounds.values.forEach {
                    if (it.uplinkTotal + it.downlinkTotal != 0L) {
                        it.proxyEntity.tx += it.uplinkTotal
                        it.proxyEntity.rx += it.downlinkTotal
                        toUpdate.add(it.proxyEntity)
                    }
                }

                if (toUpdate.isNotEmpty()) {
                    SagerDatabase.proxyDao.updateProxy(toUpdate)
                }
            } catch (e: IOException) {
                if (!DataStore.directBootAware) throw e // we should only reach here because we're in direct boot
                val profile = DirectBoot.getDeviceProfile()!!
                profile.tx += outboundStats.uplinkTotal
                profile.rx += outboundStats.downlinkTotal
                profile.dirty = true
                DirectBoot.update(profile)
                DirectBoot.listenForUnlock()
            }
        }
    }

}
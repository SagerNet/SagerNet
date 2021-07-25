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

package io.nekohasekai.sagernet.bg

import cn.hutool.core.util.NumberUtil
import com.v2ray.core.app.observatory.command.GetOutboundStatusRequest
import com.v2ray.core.app.observatory.command.ObservatoryServiceGrpcKt
import com.v2ray.core.app.stats.command.GetStatsRequest
import com.v2ray.core.app.stats.command.StatsServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.StatusException
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.DirectBoot
import kotlinx.coroutines.*
import libv2ray.Libv2ray
import java.io.IOException

class ProxyInstance(profile: ProxyEntity, val service: BaseService.Interface) : V2RayInstance(profile) {

    lateinit var managedChannel: ManagedChannel
    val statsService by lazy { StatsServiceGrpcKt.StatsServiceCoroutineStub(managedChannel) }
    val observatoryService by lazy {
        ObservatoryServiceGrpcKt.ObservatoryServiceCoroutineStub(managedChannel)
    }
    lateinit var observatoryJob: Job

    override fun initInstance() {
        if (service is VpnService) {
            v2rayPoint = Libv2ray.newV2RayPoint(SagerSupportSet(service), false)
        } else {
            super.initInstance()
        }
    }

    override fun init() {
        super.init()

        Logs.d(config.config)
        pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override fun launch() {
        super.launch()

        if (config.enableApi) {
            managedChannel = createChannel()
        }

        if (config.observatoryTags.isNotEmpty()) {
            observatoryJob = runOnDefaultDispatcher {
                val interval = 10000L
                while (isActive) {
                    try {
                        val statusList =
                            observatoryService.getOutboundStatus(GetOutboundStatusRequest.getDefaultInstance()).status.statusList
                        if (!isActive) break
                        statusList.forEach { status ->
                            val profileId = status.outboundTag.substringAfter("global-")
                            if (NumberUtil.isLong(profileId)) {
                                val profile = SagerDatabase.proxyDao.getById(profileId.toLong())
                                if (profile != null) {
                                    val newStatus = if (status.alive) 1 else 3
                                    val newDelay = status.delay.toInt()
                                    val newErrorReason = status.lastErrorReason

                                    if (profile.status != newStatus || profile.ping != newDelay || profile.error != newErrorReason) {
                                        profile.status = newStatus
                                        profile.ping = newDelay
                                        profile.error = newErrorReason
                                        SagerDatabase.proxyDao.updateProxy(profile)
                                        onMainDispatcher {
                                            service.data.binder.broadcast {
                                                it.profilePersisted(profile.id)
                                            }
                                        }
                                        Logs.d("Send result for #$profileId ${profile.displayName()}")
                                    }
                                } else {
                                    Logs.d("Profile with id #$profileId not found")
                                }
                            } else {
                                Logs.d("Persist skipped on outbound ${status.outboundTag}")
                            }
                        }
                    } catch (e: StatusException) {
                        if (closed) break
                        Logs.w(e)
                    }
                    delay(interval)
                }
            }
        }
    }

    override fun destroy(scope: CoroutineScope) {
        persistStats()
        super.destroy(scope)

        if (::observatoryJob.isInitialized) {
            observatoryJob.cancel()
        }

        if (::managedChannel.isInitialized) {
            managedChannel.shutdownNow()
        }
    }

    // ------------- stats -------------

    private suspend fun queryStats(tag: String, direct: String): Long {
        if (USE_STATS_SERVICE) {
            try {
                return queryStatsGrpc(tag, direct)
            } catch (e: StatusException) {
                if (closed) return 0L
                Logs.w(e)
                if (isExpert) return 0L
            }
        }
        return v2rayPoint.queryStats(tag, direct)
    }

    private suspend fun queryStatsGrpc(tag: String, direct: String): Long {
        if (!::managedChannel.isInitialized) {
            return 0L
        }
        try {
            return statsService.getStats(GetStatsRequest.newBuilder()
                .setName("outbound>>>$tag>>>traffic>>>$direct")
                .setReset(true)
                .build()).stat.value
        } catch (e: StatusException) {
            if (e.status.description?.contains("not found") == true) {
                return 0L
            }
            throw e
        }
    }

    private val currentTags by lazy {
        mapOf(* config.outboundTagsCurrent.map {
            it to config.outboundTagsAll[it] as ProxyEntity?
        }.toTypedArray())
    }

    private val statsTags by lazy {
        mapOf(*  config.outboundTags.toMutableList().apply {
            removeAll(config.outboundTagsCurrent)
        }.map {
            it to config.outboundTagsAll[it] as ProxyEntity?
        }.toTypedArray())
    }

    private val interTags by lazy {
        config.outboundTagsAll.filterKeys { !config.outboundTags.contains(it) }
    }

    class OutboundStats(val proxyEntity: ProxyEntity, var uplinkTotal: Long = 0L, var downlinkTotal: Long = 0L)

    private val statsOutbounds = hashMapOf<Long, OutboundStats>()
    private fun registerStats(proxyEntity: ProxyEntity, uplink: Long? = null, downlink: Long? = null) {
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
            queryStats(tag, "uplink").apply { profile?.also { registerStats(it, uplink = this) } }
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

    suspend fun directStats(direct: String): Long {
        if (!isInitialized()) return 0L
        return queryStats(config.directTag, direct)
    }

    suspend fun uplinkDirect() = directStats("uplink").also {
        uplinkTotalDirect += it
    }

    suspend fun downlinkDirect() = directStats("downlink").also {
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
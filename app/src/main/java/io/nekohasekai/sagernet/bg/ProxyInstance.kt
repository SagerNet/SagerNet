package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.v2ray.V2rayConfig
import io.nekohasekai.sagernet.fmt.v2ray.buildV2rayConfig
import io.nekohasekai.sagernet.ktx.Logs
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import java.io.IOException

class ProxyInstance(val profile: ProxyEntity) {

    lateinit var v2rayPoint: V2RayPoint
    lateinit var config: V2rayConfig
    lateinit var service: VpnService

    fun init(service: BaseService.Interface) {
        v2rayPoint = Libv2ray.newV2RayPoint(SagerSupportClass(if (service is VpnService)
            service else null), false)
        v2rayPoint.domainName =
            profile.requireBean().serverAddress + ":" + profile.requireBean().serverPort
        config = buildV2rayConfig(profile.requireBean(),
            if (DataStore.allowAccess) "0.0.0.0" else "127.0.0.1",
            DataStore.socks5Port
        )
        v2rayPoint.configureFileContent = gson.toJson(config).also {
            Logs.d(it)
        }
    }

    fun start() {
        v2rayPoint.runLoop(DataStore.preferIpv6)
    }

    fun stop() {
        v2rayPoint.stopLoop()
    }

    fun printStats() {
        val tags = config.outbounds.map { outbound -> outbound.tag.takeIf { !it.isNullOrBlank() } }
        for (tag in tags) {
            val uplink = v2rayPoint.queryStats(tag, "uplink")
            val downlink = v2rayPoint.queryStats(tag, "downlink")
            println("$tag >> uplink $uplink / downlink $downlink")
        }
    }

    fun stats(direct: String): Long {
        if (!::v2rayPoint.isInitialized) {
            return 0L
        }
        return v2rayPoint.queryStats("out", direct)
    }

    val uplink
        get() = stats("uplink").also {
            uplinkTotal += it
        }

    val downlink
        get() = stats("downlink").also {
            downlinkTotal += it
        }

    var uplinkTotal = 0L
    var downlinkTotal = 0L

    fun persistStats() {
        try {
            profile.tx += uplinkTotal
            profile.rx += downlinkTotal
            uplinkTotal = 0L
            downlinkTotal = 0L
            SagerDatabase.proxyDao.updateProxy(profile)
        } catch (e: IOException) {
            /*  if (!DataStore.directBootAware) throw e*/ // we should only reach here because we're in direct boot
        }
    }

    private class SagerSupportClass(val service: VpnService?) : V2RayVPNServiceSupportsSet {

        override fun onEmitStatus(p0: Long, status: String): Long {
            Logs.i("onEmitStatus $status")
            return 0L
        }

        override fun prepare(): Long {
            return 0L
        }

        override fun protect(l: Long): Boolean {
            return (service ?: return true).protect(l.toInt())
        }

        override fun setup(p0: String?): Long {
            return 0
        }

        override fun shutdown(): Long {
            return 0
        }
    }


}
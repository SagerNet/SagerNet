package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.buildV2rayConfig
import io.nekohasekai.sagernet.ktx.Logs
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import java.io.IOException

class ProxyInstance(val profile: ProxyEntity) {

    lateinit var v2rayPoint: V2RayPoint
    lateinit var service: VpnService

    fun init(service: BaseService.Interface) {
        v2rayPoint = Libv2ray.newV2RayPoint(SagerSupportClass(if (service is VpnService)
            service else null), false)
        if (profile.requireBean() is SOCKSBean) {
            val socks = profile.requireSOCKS()
            v2rayPoint.domainName = socks.serverAddress + ":" + socks.serverPort
            v2rayPoint.configureFileContent = buildV2rayConfig(socks,
                if (DataStore.allowAccess) "0.0.0.0" else "127.0.0.1",
                DataStore.socks5Port
            )
        }
    }

    fun start() {
        v2rayPoint.runLoop(DataStore.preferIpv6)
    }

    fun stop() {
        v2rayPoint.stopLoop()
    }

    val uplink
        get() = if (!::v2rayPoint.isInitialized) -1L else v2rayPoint.queryStats("out",
            "uplink")
    val downlink
        get() = if (!::v2rayPoint.isInitialized) -1L else v2rayPoint.queryStats("out",
            "downlink")

    fun persistStats() {
        try {
            profile.tx += uplink
            profile.rx += downlink
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
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

import android.annotation.SuppressLint
import android.os.Build
import android.system.OsConstants
import cn.hutool.cache.impl.LFUCacheCompact
import cn.hutool.core.util.HexUtil
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.utils.PackageCache
import libcore.UidDumper
import libcore.UidInfo
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress

object UidDumper : UidDumper {

    private val TCP_IPV4_PROC = File("/proc/net/tcp")
    private val TCP_IPV6_PROC = File("/proc/net/tcp6")
    private val UDP_IPV4_PROC = File("/proc/net/udp")
    private val UDP_IPV6_PROC = File("/proc/net/udp6")

    private data class ProcStats constructor(val remoteAddress: InetSocketAddress, val uid: Int)

    private fun mkMap() = LFUCacheCompact<Int, ProcStats>(-1, 5 * 60 * 1000L).build(false)

    private val uidCacheMapTcp = mkMap()
    private val uidCacheMapTcp6 = mkMap()
    private val uidCacheMapUdp = mkMap()
    private val uidCacheMapUdp6 = mkMap()

    private val canReadProc = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    private val useApi = !canReadProc/* || BuildConfig.DEBUG && tun.enableLog)*/

    override fun dumpUid(
        ipv6: Boolean, udp: Boolean, srcIp: String, srcPort: Int, destIp: String, destPort: Int
    ): Int {
        return dumpUid(
            ipv6, udp, InetSocketAddress(srcIp, srcPort), InetSocketAddress(destIp, destPort)
        )
    }

    override fun getUidInfo(uid: Int): UidInfo {
        PackageCache.awaitLoadSync()

        if (uid <= 1000L) {
            val uidInfo = UidInfo()
            uidInfo.label = PackageCache.loadLabel("android")
            uidInfo.packageName = "android"
            return uidInfo
        }

        val packageNames = PackageCache.uidMap[uid.toInt()]
        if (!packageNames.isNullOrEmpty()) for (packageName in packageNames) {
            val uidInfo = UidInfo()
            uidInfo.label = PackageCache.loadLabel(packageName)
            uidInfo.packageName = packageName
            return uidInfo
        }

        error("unknown uid $uid")
    }

    @SuppressLint("NewApi")
    fun dumpUid(
        ipv6: Boolean, udp: Boolean, local: InetSocketAddress, remote: InetSocketAddress
    ): Int {

        if (useApi) return SagerNet.connectivity.getConnectionOwnerUid(
            if (!udp) OsConstants.IPPROTO_TCP else OsConstants.IPPROTO_UDP, local, remote
        )

        val proc = if (!udp) {
            if (!ipv6) TCP_IPV4_PROC else TCP_IPV6_PROC
        } else {
            if (!ipv6) UDP_IPV4_PROC else UDP_IPV6_PROC
        }

        val cacheMap = if (!udp) {
            if (!ipv6) uidCacheMapTcp else uidCacheMapTcp6
        } else {
            if (!ipv6) uidCacheMapUdp else uidCacheMapUdp6
        }

        if (cacheMap.containsKey(local.port)) {
            val cache = cacheMap[local.port]
            if (cache.remoteAddress == remote) return cache.uid
        }

        var lines = proc.readLines().map { line ->
            line.split(" ").filterNot { it.isBlank() }
        }
        lines = lines.subList(1, lines.size)

        for (process in lines) {
            val localPort = process[1].substringAfter(":").toInt(16)
            val remoteAddress = InetAddress.getByAddress(
                HexUtil.decodeHex(
                    process[2].substringBefore(
                        ":"
                    )
                )
            )
            val remotePort = process[2].substringAfter(":").toInt(16)
            val uid = process[7].toInt()
            cacheMap.put(
                localPort, ProcStats(InetSocketAddress(remoteAddress, remotePort), uid)
            )
        }

        return cacheMap[local.port]?.uid ?: -1
    }

}

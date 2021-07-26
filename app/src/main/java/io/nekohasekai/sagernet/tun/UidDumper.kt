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

package io.nekohasekai.sagernet.tun

import android.annotation.SuppressLint
import android.os.Build
import android.system.OsConstants
import cn.hutool.cache.impl.LFUCache
import cn.hutool.core.util.HexUtil
import com.topjohnwu.superuser.io.SuFileInputStream
import io.nekohasekai.sagernet.SagerNet
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class UidDumper(val tun: TunThread) {

    private companion object {

        val TCP_IPV4_PROC = File("/proc/net/tcp")
        val TCP_IPV6_PROC = File("/proc/net/tcp6")
        val UDP_IPV4_PROC = File("/proc/net/udp")
        val UDP_IPV6_PROC = File("/proc/net/udp6")

    }

    private data class ProcStats constructor(val remoteAddress: InetSocketAddress, val uid: Int)
    private inner class TunMap : LFUCache<Int, ProcStats>(-1, 5 * 60 * 1000L) {
        init {
            if (tun.multiThreadForward) {
                cacheMap = ConcurrentHashMap()
            }
        }
    }

    private val uidCacheMapTcp = TunMap()
    private val uidCacheMapTcp6 = TunMap()
    private val uidCacheMapUdp = TunMap()
    private val uidCacheMapUdp6 = TunMap()

    private val canReadProc = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    private val useApi = !canReadProc/* || BuildConfig.DEBUG && tun.enableLog)*/

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

        var lines = (if (!canReadProc) SuFileInputStream.open(proc)
            .bufferedReader()
            .readLines() else proc.readLines()).map { line ->
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

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

import android.system.ErrnoException
import cn.hutool.core.io.IoUtil
import io.nekohasekai.sagernet.PacketStrategy
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.tun.ip.NetUtils
import io.nekohasekai.sagernet.tun.ip.ipv4.ICMPHeader
import io.nekohasekai.sagernet.tun.ip.ipv4.IPv4Header
import io.nekohasekai.sagernet.tun.ip.ipv6.ICMPv6Header
import io.nekohasekai.sagernet.tun.ip.ipv6.IPv6Header
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.random.Random

class TunThread(val service: VpnService) : Thread("TUN Thread") {

    private lateinit var fd: FileDescriptor

    val closed get() = service.data.proxy?.closed == true
    val loggingHandler = LoggingHandler(LogLevel.TRACE)
    val serverLoop = NioEventLoopGroup()
    val outboundLoop = NioEventLoopGroup()

    val tcpForwarder = TcpForwarder(this)
    val udpForwarder = UdpForwarder(this)

    val socksPort = DataStore.socksPort
    val dnsPort = DataStore.localDNSPort
    val uidDumper = UidDumper(this)
    val uidMap = service.data.proxy?.config?.uidMap ?: emptyMap()

    init {
        Logs.d("UIDs: $uidMap")
    }

    override fun interrupt() {
        running = false
        IoUtil.close(input)

        super.interrupt()
        tcpForwarder.destroy()
        udpForwarder.destroy()
        service.conn.close()
    }

    var start = 0L
    val enableLog = DataStore.enableLog
    val dumpUid = enableLog || uidMap.isNotEmpty()
    val multiThreadForward = DataStore.multiThreadForward

    val buffer = ByteArray(VpnService.VPN_MTU)
    lateinit var input: FileInputStream
    lateinit var output: FileOutputStream
    fun write(data: ByteArray, length: Int) {
        output.write(data, 0, length)
        output.flush()
    }

    @Volatile
    var running = true

    override fun run() {
        fd = service.conn.fileDescriptor
        input = FileInputStream(fd)
        output = FileOutputStream(fd)
        tcpForwarder.start()

        runBlocking {
            if (!multiThreadForward) loopSingleThread() else loopMultiThread()
        }
    }

    suspend fun loopSingleThread() {
        do {
            val length = try {
                input.read(buffer)
            } catch (e: IOException) {
                break
            }
            processPacket(buffer, length)
        } while (running)
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    suspend fun loopMultiThread() {
        do {
            val length = try {
                input.read(buffer)
            } catch (e: IOException) {
                break
            }
            val packet = buffer.copyOf(length)
            GlobalScope.launch(Dispatchers.Default) {
                processPacket(packet, length)
            }
        } while (running)
    }

    suspend fun processPacket(packet: ByteArray, length: Int) {

        val ipHeader = when (packet[0].toInt() ushr 4) {
            4 -> IPv4Header(packet, length)
            6 -> IPv6Header(packet, length)
            else -> {
                processOther(packet, length)
                return
            }
        }

        try {
            when (ipHeader.protocol) {
                NetUtils.IPPROTO_ICMP -> processICMP(packet, ipHeader as IPv4Header)
                NetUtils.IPPROTO_ICMPv6 -> processICMPv6(packet, ipHeader as IPv6Header)
                NetUtils.IPPROTO_TCP -> tcpForwarder.processTcp(packet, ipHeader)
                NetUtils.IPPROTO_UDP -> udpForwarder.processUdp(packet, ipHeader)
                else -> processOther(packet, length)
            }
        } catch (e: InterruptedException) {
            interrupt()
            IoUtil.close(input)
            IoUtil.close(output)
            running = false
            return
        } catch (e: ErrnoException) {
            Logs.w(e)
            interrupt()
            IoUtil.close(input)
            IoUtil.close(output)
            running = false
            return
        } catch (e: Throwable) {
            Logs.w(e)
        }

    }

    val icmpEchoStrategy = DataStore.icmpEchoStrategy
    val icmpEchoReplyDelay = DataStore.icmpEchoReplyDelay

    private fun mkDelay(): Long {
        return when {
            icmpEchoReplyDelay <= 0 -> icmpEchoReplyDelay
            Random.nextInt(30) == 0 -> icmpEchoReplyDelay + Random.nextInt(30, 100)
            else -> icmpEchoReplyDelay + Random.nextInt(-10, 10)
        }
    }

    suspend fun processICMP(packet: ByteArray, ipHeader: IPv4Header) {

        val icmpHeader = ICMPHeader(ipHeader)
        if (icmpHeader.type.toInt() == 8) {
            when (icmpEchoStrategy) {
                PacketStrategy.DIRECT -> write(packet, ipHeader.packetLength)
                PacketStrategy.DROP -> return
                PacketStrategy.REPLY -> {
                    ipHeader.revertAddress()
                    icmpHeader.revertEcho()
                    delay(mkDelay())
                    write(packet, ipHeader.packetLength)
                }
            }
        } else {
            processOther(packet, ipHeader.packetLength)
        }

    }

    suspend fun processICMPv6(packet: ByteArray, ipHeader: IPv6Header) {

        val icmpHeader = ICMPv6Header(ipHeader)

        if (icmpHeader.type == 128) {
            when (icmpEchoStrategy) {
                PacketStrategy.DIRECT -> write(packet, ipHeader.packetLength)
                PacketStrategy.DROP -> return
                PacketStrategy.REPLY -> {
                    ipHeader.revertAddress()
                    icmpHeader.revertEcho()
                    delay(mkDelay())
                    write(packet, ipHeader.packetLength)
                }
            }
        } else {
            processOther(packet, ipHeader.packetLength)
        }
    }

    val ipOtherStrategy = DataStore.ipOtherStrategy
    fun processOther(packet: ByteArray, length: Int) {
        when (ipOtherStrategy) {
            PacketStrategy.DIRECT -> write(packet, length)
            PacketStrategy.DROP -> return
        }
    }

}
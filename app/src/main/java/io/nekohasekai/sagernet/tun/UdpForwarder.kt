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

import android.os.SystemClock
import cn.hutool.cache.impl.LFUCache
import cn.hutool.core.util.ArrayUtil
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.LAUNCH_DELAY
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.tun.ip.IPHeader
import io.nekohasekai.sagernet.tun.ip.UDPHeader
import io.nekohasekai.sagernet.tun.ip.ipv4.IPv4Header
import io.nekohasekai.sagernet.tun.ip.ipv6.IPv6Header
import io.nekohasekai.sagernet.tun.netty.DefaultSocks5UdpMessage
import io.nekohasekai.sagernet.tun.netty.Socks5UdpMessage
import io.nekohasekai.sagernet.tun.netty.Socks5UdpMessageDecoder
import io.nekohasekai.sagernet.tun.netty.Socks5UdpMessageEncoder
import io.nekohasekai.sagernet.utils.PackageCache
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.util.concurrent.DefaultPromise
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GlobalEventExecutor
import okhttp3.internal.connection.RouteSelector.Companion.socketHost
import org.xbill.DNS.Flags
import org.xbill.DNS.Header
import org.xbill.DNS.Message
import org.xbill.DNS.Section
import java.net.InetSocketAddress

class UdpForwarder(val tun: TunThread) {

    private val udpSessions = object : LFUCache<Short, UdpSession>(-1, 5 * 60 * 1000L) {
        override fun onRemove(key: Short, session: UdpSession) {
            session.destroy()
        }
    }

    fun processUdp(packet: ByteArray, ipHeader: IPHeader) {
        val udpHeader = UDPHeader(ipHeader)
        val localPort = udpHeader.sourcePort
        val remoteIp = ipHeader.destinationAddress
        val remotePort = udpHeader.destinationPort
        val remotePortInt = remotePort.toUShort().toInt()
        if (remotePortInt == tun.dnsPort) {
            tun.write(packet, ipHeader.packetLength)
            return
        }

        var session = udpSessions[localPort]
        if (session != null && (session.remotePort != remotePort || session.remoteIp != remoteIp || session.future.now?.isActive == false)) {
            udpSessions.remove(localPort)
            session.destroy()
            session = null
        }

        if (session == null) {
            session = UdpSession(udpHeader)
            udpSessions.put(localPort, session)

            if (tun.dumpUid) {

                session.uid = tun.uidDumper.dumpUid(
                    ipHeader is IPv6Header, true, session.localAddress, session.destAddress
                )

                if (tun.enableLog) {

                    if (session.uid > 0) {
                        session.packages = PackageCache[session.uid]
                    } else if (session.uid == 0) {
                        session.packages = listOf("root")
                    }

                    Logs.d(
                        "Accepted udp connection from ${session.packages?.joinToString() ?: "unknown"} (${session.uid}): ${ipHeader.sourceAddress.hostAddress}:${
                            udpHeader.sourcePort.toUShort().toInt()
                        } ==> ${ipHeader.destinationAddress}:${
                            udpHeader.destinationPort.toUShort().toInt()
                        }"
                    )

                }
            } else if (tun.enableLog) {
                Logs.d(
                    "Accepted udp connection ${ipHeader.sourceAddress.hostAddress}:${
                        udpHeader.sourcePort.toUShort().toInt()
                    } ==> ${ipHeader.destinationAddress}:${
                        udpHeader.destinationPort.toUShort().toInt()
                    }"
                )
            }

            session.connect()
        } else {
            session.udpHeader = udpHeader
        }
        val data = Unpooled.wrappedBuffer(udpHeader.data())

        val message: Any = if (session.isDns) DatagramPacket(
            data, InetSocketAddress(
                LOCALHOST, tun.dnsPort
            )
        ) else DefaultSocks5UdpMessage(
            (0).toByte(),
            if (ipHeader is IPv4Header) Socks5AddressType.IPv4 else Socks5AddressType.IPv6,
            session.destAddress.socketHost,
            session.destAddress.port,
            data
        )

        session.future.sync().get().writeAndFlush(message)
    }

    inner class UdpSession(var udpHeader: UDPHeader) : ChannelInboundHandlerAdapter() {

        val localIp = udpHeader.ipHeader.sourceAddress
        val localPort = udpHeader.sourcePort
        val remoteIp = udpHeader.ipHeader.destinationAddress
        val remotePort = udpHeader.destinationPort

        val localAddress = InetSocketAddress(localIp, localPort.toUShort().toInt())
        val destAddress = InetSocketAddress(remoteIp, remotePort.toUShort().toInt())

        val time = SystemClock.elapsedRealtime() + LAUNCH_DELAY
        var uid = 0
        var packages: Collection<String>? = null
        var isDns = false
        var isSelf = false
        var template = createTemplate(udpHeader).header()

        private val promise = DefaultPromise<Channel>(GlobalEventExecutor.INSTANCE)
        val future: Future<Channel> get() = promise

        private fun calIsDns(): Boolean {
            if (destAddress.socketHost in arrayOf(
                    VpnService.PRIVATE_VLAN4_ROUTER, VpnService.PRIVATE_VLAN6_ROUTER
                )
            ) return true
            if (udpHeader.destinationPort.toUShort().toInt() == 53) return true
            val data = udpHeader.data()
            if (data.size < Header.LENGTH) return false
            return try {
                val dnsMessage = Message(data)

                !dnsMessage.header.getFlag(Flags.QR.toInt()) && dnsMessage.rcode == 0 && dnsMessage.header.getCount(
                    Section.ANSWER
                ) == 0 && dnsMessage.header.getCount(Section.AUTHORITY) == 0
            } catch (e: Exception) {
                false
            }
        }

        fun connect() {
            if (calIsDns()) {
                isDns = true
                Bootstrap().group(tun.outboundLoop)
                    .channel(NioDatagramChannel::class.java)
                    .handler(this)
                    .connect(LOCALHOST, tun.dnsPort)
            } else {
                Bootstrap().group(tun.outboundLoop)
                    .channel(NioDatagramChannel::class.java)
                    .handler(object : ChannelInitializer<Channel>() {
                        override fun initChannel(channel: Channel) {
                            channel.pipeline().apply {
                                addFirst(Socks5UdpMessageEncoder.DEFAULT, Socks5UdpMessageDecoder())
                                addLast(this@UdpSession)
                            }
                        }
                    })
                    .connect(LOCALHOST, tun.uidMap[uid] ?: tun.socksPort)
            }.addListener(ChannelFutureListener {
                if (it.isSuccess) {
                    promise.trySuccess(it.channel())
                } else {
                    promise.tryFailure(it.cause())
                }
            })
        }

        fun destroy() {
            future.now?.close()
        }

        private fun createTemplate(udpHeader: UDPHeader): UDPHeader {
            return udpHeader.copy().apply {
                ipHeader.revertAddress()
                revertPort()
            }
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            when (msg) {
                is Socks5UdpMessage -> sendResponse(msg.data().array())
                is DatagramPacket -> sendResponse(msg.content().array())
            }
        }

        fun sendResponse(rawData: ByteArray) {

            val data = if (!isDns) rawData else {

                // v2ray sends 2kb shit for every dns response
                ArrayUtil.sub(rawData, 0, Message(rawData).numBytes())
            }

            val packet = ArrayUtil.addAll(template, data)

            if (packet.size > VpnService.VPN_MTU) {
                Logs.w("Ingoing too big udp package, size: ${packet.size}")
                return
            }

            val ipHeader: IPHeader
            if (udpHeader.ipHeader is IPv4Header) {
                ipHeader = IPv4Header(packet)
                ipHeader.setTotalLength(packet.size.toShort())
                ipHeader.updateChecksum()
            } else {
                ipHeader = IPv6Header(packet)
                ipHeader.payloadLength = ipHeader.packetLength - ipHeader.headerLength
            }

            val udpHeader = UDPHeader(ipHeader)

            udpHeader.setTotalLength((packet.size - ipHeader.headerLength).toShort())
            udpHeader.updateChecksum()

            tun.write(packet, ipHeader.packetLength)

            if (isDns) udpSessions.remove(localPort)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            ctx.close()
            if (!tun.closed) Logs.w(cause)
        }

    }

    fun destroy() {
        for (session in udpSessions) {
            session.destroy()
        }
    }

}
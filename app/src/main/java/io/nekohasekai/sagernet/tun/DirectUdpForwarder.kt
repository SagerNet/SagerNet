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
import cn.hutool.cache.impl.LFUCacheCompact
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.LAUNCH_DELAY
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.tun.ip.DirectIPHeader
import io.nekohasekai.sagernet.tun.ip.DirectIPv4Header
import io.nekohasekai.sagernet.tun.ip.DirectIPv6Header
import io.nekohasekai.sagernet.tun.ip.DirectUdpHeader
import io.nekohasekai.sagernet.tun.netty.DefaultSocks5UdpMessage
import io.nekohasekai.sagernet.tun.netty.Socks5UdpMessage
import io.nekohasekai.sagernet.tun.netty.Socks5UdpMessageDecoder
import io.nekohasekai.sagernet.tun.netty.Socks5UdpMessageEncoder
import io.nekohasekai.sagernet.utils.PackageCache
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.DatagramPacket
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.util.IllegalReferenceCountException
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import java.net.InetSocketAddress

class DirectUdpForwarder(val tun: DirectTunThread) {

    private val udpSessions = object : LFUCacheCompact<Int, UdpSession>(-1, 5 * 60 * 1000L) {
        override fun onRemove(key: Int, session: UdpSession) {
            session.destroy()
        }
    }.build(tun.multiThreadForward)

    fun processUdp(packet: ByteBuf, ipHeader: DirectIPHeader) {
        val udpHeader = DirectUdpHeader(ipHeader)
        val localIp = ipHeader.sourceInetAddress
        val localPort = udpHeader.sourcePort
        val remoteIp = ipHeader.destinationInetAddress
        val remotePort = udpHeader.destinationPort

        var session = udpSessions[localPort]
        if (session != null && (session.remotePort != remotePort || session.remoteIp != remoteIp || session.channelFuture.channel()?.isActive == false)) {
            udpSessions.remove(localPort)
            session.destroy()
            session = null
        }

        if (session == null) {
            session = UdpSession(udpHeader)
            udpSessions.put(localPort, session)

            if (tun.dumpUid) {

                session.uid = tun.uidDumper.dumpUid(
                    ipHeader is DirectIPv6Header,
                    true,
                    InetSocketAddress(localIp, localPort),
                    InetSocketAddress(remoteIp, remotePort)
                )

                if (tun.enableLog) {

                    if (session.uid > 0) {
                        session.packages = PackageCache[session.uid]
                    } else if (session.uid == 0) {
                        session.packages = listOf("root")
                    }

                    Logs.d(
                        "Accepted udp connection from ${session.packages?.joinToString() ?: "unknown"} (${session.uid}): ${localIp.hostAddress}:$localPort ==> ${remoteIp.hostAddress}:$remotePort"
                    )

                }
            } else if (tun.enableLog) {
                Logs.d(
                    "Accepted udp connection ${localIp.hostAddress}:$localPort ==> ${remoteIp.hostAddress}:$remotePort"
                )
            }

            session.connect()
        }


        val data = udpHeader.data().retain()
        val message: Any = if (session.isDns) DatagramPacket(
            data, InetSocketAddress(
                LOCALHOST, tun.dnsPort
            )
        ) else DefaultSocks5UdpMessage(
            (0).toByte(),
            if (ipHeader is DirectIPv4Header) Socks5AddressType.IPv4 else Socks5AddressType.IPv6,
            session.remoteIp.hostAddress,
            session.remotePort,
            data
        )

        if (session.channelFuture.isSuccess) {
            session.channelFuture.channel().writeAndFlush(message)
        } else session.channelFuture.addListener(object : GenericFutureListener<Future<Void>> {
            override fun operationComplete(future: Future<Void>) {
                if (!future.isSuccess && tun.enableLog) {
                    Logs.w(future.cause())
                    return
                }
                session.channelFuture.removeListener(this)
                session.channelFuture.channel().writeAndFlush(message)
            }
        })
    }

    inner class UdpSession(val udpHeader: DirectUdpHeader) : ChannelInboundHandlerAdapter() {

        val localIp = udpHeader.ipHeader.sourceInetAddress
        val localPort = udpHeader.sourcePort
        val remoteIp = udpHeader.ipHeader.destinationInetAddress
        val remotePort = udpHeader.destinationPort

        val time = SystemClock.elapsedRealtime() + LAUNCH_DELAY
        var uid = 0
        var packages: Collection<String>? = null
        var isDns = false
        var template = createTemplate(udpHeader)

        lateinit var channelFuture: ChannelFuture

        private fun calIsDns(): Boolean {
            if (remoteIp.hostAddress in arrayOf(
                    VpnService.PRIVATE_VLAN4_ROUTER, VpnService.PRIVATE_VLAN6_ROUTER
                )
            ) return true
            if (udpHeader.destinationPort == 53) return true
            return false
        }

        fun connect() {
            channelFuture = if (calIsDns()) {
                isDns = true
                Bootstrap().group(tun.eventLoop)
                    .channel(SagerNet.datagramChannel)
                    .handler(this)
                    .connect(LOCALHOST, tun.dnsPort)
            } else {
                Bootstrap().group(tun.eventLoop)
                    .channel(SagerNet.datagramChannel)
                    .handler(this)
                    .connect(LOCALHOST, tun.uidMap[uid] ?: tun.socksPort)
            }
        }

        override fun channelRegistered(ctx: ChannelHandlerContext) {
            if (!isDns) {
                ctx.pipeline().addFirst(
                    Socks5UdpMessageEncoder.DEFAULT, Socks5UdpMessageDecoder()
                )
            }
        }

        fun destroy() {
            runCatching {
                template.release()
            }
            channelFuture.channel().close()
        }

        private fun createTemplate(udpHeader: DirectUdpHeader): ByteBuf {
            return udpHeader.copyHeader().apply {
                ipHeader.revertAddress()
                revertPort()
            }.header()
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            when (msg) {
                is Socks5UdpMessage -> sendResponse(msg.data())
                is DatagramPacket -> sendResponse(msg.content())
                is ByteBuf -> sendResponse(msg)
            }
            ReferenceCountUtil.release(msg)
        }

        fun sendResponse(rawData: ByteBuf) {
            /* val data = if (isDns) {
                 Logs.d("DNS Response ${rawData.readableBytes()}B")
                 rawData.slice(
                     0, Message(rawData.internalNioBuffer(0, rawData.readableBytes())).numBytes()
                 )
             } else rawData*/

            val template = try {
                template.slice()
            } catch (e: IllegalReferenceCountException) {
                return
            }
            val packetSize = template.readableBytes() + rawData.readableBytes()
            if (packetSize > VpnService.VPN_MTU) {
                Logs.w("Ingoing too big udp package, size: $packetSize")
                return
            }

            val packet = rawData.alloc().directBuffer(packetSize, packetSize)
            packet.writeBytes(template)
            packet.writeBytes(rawData)

            val ipHeader: DirectIPHeader
            if (udpHeader.ipHeader is DirectIPv4Header) {
                ipHeader = DirectIPv4Header(packet, packetSize)
                ipHeader.totalLength = packetSize
                ipHeader.updateChecksum()
            } else {
                ipHeader = DirectIPv6Header(packet, packetSize)
                ipHeader.dataLength = ipHeader.packetLength - ipHeader.headerLength
            }

            val udpHeader = DirectUdpHeader(ipHeader)
            udpHeader.totalLength = packetSize - ipHeader.headerLength
            udpHeader.updateChecksum()

            tun.write(packet, packetSize)
            packet.release()

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
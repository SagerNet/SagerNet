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
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.INET6_TUN
import io.nekohasekai.sagernet.ktx.INET_TUN
import io.nekohasekai.sagernet.ktx.LAUNCH_DELAY
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.tun.ip.IPHeader
import io.nekohasekai.sagernet.tun.ip.TCPHeader
import io.nekohasekai.sagernet.tun.ip.ipv4.IPv4Header
import io.nekohasekai.sagernet.tun.ip.ipv6.IPv6Header
import io.nekohasekai.sagernet.utils.PackageCache
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.proxy.Socks5ProxyHandler
import okhttp3.internal.connection.RouteSelector.Companion.socketHost
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class TcpForwarder(val tun: TunThread) {

    private val tcpSessions = object : LFUCache<Short, TcpSession>(-1, 5 * 60 * 1000L) {
        init {
            if (tun.enableLog) cacheMap = ConcurrentHashMap()
        }

        override fun onRemove(key: Short, session: TcpSession) {
            session.channel?.close()
        }
    }

    class TcpSession(
        val localAddress: InetAddress,
        val localPort: Short,
        val remoteAddress: InetAddress,
        val remotePort: Short
    ) {
        val time = SystemClock.elapsedRealtime() + LAUNCH_DELAY
        var uid = 0
        var packages: Collection<String>? = null
        var channel: Channel? = null
    }

    fun processTcp(packet: ByteArray, ipHeader: IPHeader) {

        val tcpHeader = TCPHeader(ipHeader, packet, ipHeader.headerLength)
        val localIp = ipHeader.sourceAddress
        val localPort = tcpHeader.sourcePort
        val remoteIp = ipHeader.destinationAddress
        val remotePort = tcpHeader.destinationPort

        if (localPort != forwardServerPort) {

            var session = tcpSessions[localPort]
            if (session != null && (session.remotePort != remotePort || session.remoteAddress != remoteIp)) {
                session.channel?.close()
                tcpSessions.remove(localPort)
            }
            if (session == null) {
                session = TcpSession(localIp, localPort, remoteIp, remotePort)
                tcpSessions.put(localPort, session)
                if (tun.dumpUid) {
                    val localAddress = InetSocketAddress(
                        session.localAddress,
                        session.localPort.toUShort().toInt(),
                    )
                    val remoteAddress = InetSocketAddress(
                        session.remoteAddress, session.remotePort.toUShort().toInt()
                    )

                    session.uid = tun.uidDumper.dumpUid(
                        ipHeader is IPv6Header, false, localAddress, remoteAddress
                    )

                    if (tun.enableLog) {
                        if (session.uid > 0) {
                            session.packages = PackageCache[session.uid]
                        } else if (session.uid == 0) {
                            session.packages = listOf("root")
                        }

                        Logs.d(
                            "Accepted tcp connection from ${session.packages?.joinToString() ?: "unknown"} (${session.uid}): ${ipHeader.sourceAddress.hostAddress}:${
                                tcpHeader.sourcePort.toUShort().toInt()
                            } ==> ${ipHeader.destinationAddress}:${
                                tcpHeader.destinationPort.toUShort().toInt()
                            }"
                        )
                    }

                } else if (tun.enableLog) {
                    Logs.d(
                        "Accepted tcp connection ${ipHeader.sourceAddress.hostAddress}:${
                            tcpHeader.sourcePort.toUShort().toInt()
                        } ==> ${ipHeader.destinationAddress}:${
                            tcpHeader.destinationPort.toUShort().toInt()
                        }"
                    )

                }

            }

            ipHeader.sourceAddress = remoteIp
            tcpHeader.destinationPort = forwardServerPort

            if (ipHeader is IPv4Header) {
                ipHeader.destinationAddress = INET_TUN
                ipHeader.updateChecksum()

            } else {
                ipHeader.destinationAddress = INET6_TUN
            }

            tcpHeader.updateChecksum()

        } else {

            val session = tcpSessions[remotePort]
            if (session == null) {
                if (tun.enableLog) Logs.w("No session saved with key: $remotePort")
                return
            }

            ipHeader.sourceAddress = remoteIp
            tcpHeader.sourcePort = session.remotePort

            if (ipHeader is IPv4Header) {
                ipHeader.destinationAddress = INET_TUN
                ipHeader.updateChecksum()

            } else {
                ipHeader.destinationAddress = INET6_TUN
            }

            tcpHeader.updateChecksum()

        }

        tun.write(packet, ipHeader.packetLength)
    }

    inner class Forwarder : ChannelInboundHandlerAdapter() {

        lateinit var channelFeature: ChannelFuture
        var localPort: Short = 0
        var isDns = false

        override fun channelActive(ctx: ChannelHandlerContext) {

            val clientAddress = ctx.channel().remoteAddress() as InetSocketAddress
            var remoteIp = clientAddress.socketHost
            localPort = clientAddress.port.toShort()
            val session = tcpSessions[localPort]
            if (session == null) {
                if (tun.enableLog) Logs.w("No session saved with key: $localPort")
                ctx.close()
                return
            }

            var remotePort = session.remotePort.toUShort().toInt()
            if (remotePort == 53 || remoteIp in arrayOf(
                    VpnService.PRIVATE_VLAN4_ROUTER, VpnService.PRIVATE_VLAN6_ROUTER
                )
            ) {
                isDns = true
                remoteIp = LOCALHOST
                remotePort = tun.dnsPort
            }
            if (isDns) {
                // direct for dns

                channelFeature = Bootstrap().group(tun.outboundLoop)
                    .channel(NioSocketChannel::class.java)
                    .handler(object : ChannelInitializer<Channel>() {
                        override fun initChannel(channel: Channel) {
                            channel.pipeline().addLast(ChannelForwardAdapter(ctx.channel()))
                        }
                    })
                    .connect(remoteIp, remotePort)
                    .addListener(ChannelFutureListener {
                        if (it.isSuccess) {
                            session.channel?.close()
                            session.channel = it.channel()
                        } else {
                            ctx.fireExceptionCaught(it.cause())
                        }
                    })
            } else {
                val socksPort = tun.uidMap[session.uid] ?: tun.socksPort
                channelFeature = Bootstrap().group(tun.outboundLoop)
                    .channel(NioSocketChannel::class.java)
                    .handler(object : ChannelInitializer<Channel>() {
                        override fun initChannel(channel: Channel) {
                            channel.pipeline().addFirst(
                                Socks5ProxyHandler(
                                    InetSocketAddress(
                                        LOCALHOST, socksPort
                                    )
                                )
                            )
                            channel.pipeline().addLast(ChannelForwardAdapter(ctx.channel()))
                        }
                    })
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.AUTO_CLOSE, false)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .connect(remoteIp, remotePort)
                    .addListener(ChannelFutureListener {
                        if (it.isSuccess) {
                            session.channel?.close()
                            session.channel = it.channel()
                        } else {
                            ctx.fireExceptionCaught(it.cause())
                        }
                    })
            }

        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            channelFeature.sync().channel().writeAndFlush(msg)
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            channelFeature.channel()?.close()

            if (tun.enableLog) Logs.d("Closed connection to " + ctx.channel().remoteAddress())
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            if (!(tun.closed || tun.running)) Logs.w(cause)
            ctx.close()
            channelFeature.channel()?.close()
        }

    }

    inner class ChannelForwardAdapter(val channel: Channel) : ChannelInboundHandlerAdapter() {

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            channel.writeAndFlush(msg)
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            channel.close()
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            if (!(tun.closed || tun.running)) Logs.w(cause)
            ctx.close()
            channel.close()
        }

    }

    // fwd server

    private lateinit var channelFuture: ChannelFuture
    val forwardServerPort by lazy {
        (channelFuture.sync().channel() as NioServerSocketChannel).localAddress().port.toShort()
    }

    fun start() {
        channelFuture = ServerBootstrap().group(tun.serverLoop, tun.outboundLoop)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<Channel>() {
                override fun initChannel(channel: Channel) {
                    channel.pipeline().addLast(Forwarder())
                }

                override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                    if (!(tun.closed || tun.running)) Logs.w(cause)
                }
            })
            .bind(0)
    }

    fun destroy() {
        for (session in tcpSessions) {
            session.channel?.close()
        }
        if (::channelFuture.isInitialized) {
            channelFuture.channel()?.close()
        }
    }

}
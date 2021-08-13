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
import io.nekohasekai.sagernet.ktx.INET6_TUN
import io.nekohasekai.sagernet.ktx.INET_TUN
import io.nekohasekai.sagernet.ktx.LAUNCH_DELAY
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.tun.ip.DirectIPHeader
import io.nekohasekai.sagernet.tun.ip.DirectIPv4Header
import io.nekohasekai.sagernet.tun.ip.DirectIPv6Header
import io.nekohasekai.sagernet.tun.ip.DirectTcpHeader
import io.nekohasekai.sagernet.utils.PackageCache
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.ServerSocketChannel
import io.netty.handler.proxy.Socks5ProxyHandler
import okhttp3.internal.connection.RouteSelector.Companion.socketHost
import java.net.InetAddress
import java.net.InetSocketAddress

class DirectTcpForwarder(val tun: DirectTunThread) {

    private val tcpSessions = object : LFUCacheCompact<Int, TcpSession>(-1, 5 * 60 * 1000L) {
        override fun onRemove(key: Int, session: TcpSession) {
            session.channel?.close()
        }
    }.build(tun.multiThreadForward)

    class TcpSession(
        val localAddress: InetAddress,
        val localPort: Int,
        val remoteAddress: InetAddress,
        val remotePort: Int
    ) {
        val time = SystemClock.elapsedRealtime() + LAUNCH_DELAY
        var uid = 0
        var packages: Collection<String>? = null
        var channel: Channel? = null
    }

    fun processTcp(packet: ByteBuf, ipHeader: DirectIPHeader) {

        val tcpHeader = DirectTcpHeader(ipHeader)
        val localIp = ipHeader.sourceInetAddress
        val localPort = tcpHeader.sourcePort
        val remoteIp = ipHeader.destinationInetAddress
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
                    val localAddress = InetSocketAddress(session.localAddress, session.localPort)
                    val remoteAddress = InetSocketAddress(session.remoteAddress, session.remotePort)

                    session.uid = tun.uidDumper.dumpUid(
                        ipHeader is DirectIPv6Header, false, localAddress, remoteAddress
                    )

                    if (tun.enableLog) {
                        if (session.uid > 0) {
                            session.packages = PackageCache[session.uid]
                        } else if (session.uid == 0) {
                            session.packages = listOf("root")
                        }

                        Logs.d(
                            "Accepted tcp connection from ${session.packages?.joinToString() ?: "unknown"} (${session.uid}): ${localIp.hostAddress}:$localPort ==> ${remoteIp.hostAddress}:$remotePort"
                        )
                    }

                } else if (tun.enableLog) {
                    Logs.d(
                        "Accepted tcp connection $${localIp.hostAddress}:$localPort ==> ${remoteIp.hostAddress}:$remotePort"
                    )

                }

            }

            ipHeader.sourceInetAddress = remoteIp
            tcpHeader.destinationPort = forwardServerPort

            ipHeader.destinationInetAddress = if (ipHeader is DirectIPv4Header) INET_TUN else INET6_TUN

            ipHeader.updateChecksum()
            tcpHeader.updateChecksum()

        } else {

            val session = tcpSessions[remotePort]
            if (session == null) {
                if (tun.enableLog) Logs.w("No session saved with key: $remotePort")
                return
            }

            ipHeader.sourceInetAddress = remoteIp
            tcpHeader.sourcePort = session.remotePort
            ipHeader.destinationInetAddress = if (ipHeader is DirectIPv4Header) INET_TUN else INET6_TUN
            ipHeader.updateChecksum()
            tcpHeader.updateChecksum()
        }

        tun.write(packet, ipHeader.packetLength)
    }

    inner class Forwarder : ChannelInboundHandlerAdapter() {

        lateinit var channelFeature: ChannelFuture
        var localPort = 0
        var isDns = false

        override fun channelActive(ctx: ChannelHandlerContext) {

            val clientAddress = ctx.channel().remoteAddress() as InetSocketAddress
            var remoteIp = clientAddress.socketHost
            localPort = clientAddress.port
            val session = tcpSessions[localPort]
            if (session == null) {
                if (tun.enableLog) Logs.w("No session saved with key: $localPort")
                ctx.close()
                return
            }

            var remotePort = session.remotePort
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

                channelFeature = Bootstrap().group(tun.eventLoop).channel(SagerNet.socketChannel)
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
                channelFeature = Bootstrap().group(tun.eventLoop).channel(SagerNet.socketChannel)
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

    lateinit var channelFuture: ChannelFuture
    val forwardServerPort by lazy {
        (channelFuture.sync().channel() as ServerSocketChannel).localAddress().port
    }

    fun start() {
        channelFuture = ServerBootstrap().group(tun.eventLoop).channel(SagerNet.serverSocketChannel)
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
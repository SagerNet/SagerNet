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

package io.nekohasekai.sagernet.bg.socks

import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.AbstractInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.handler.codec.socksx.v5.*
import io.netty.handler.proxy.Socks4ProxyHandler
import io.netty.resolver.dns.DnsNameResolver
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CoroutineScope
import java.net.InetSocketAddress

class Socks4To5Instance(
    val eventLoopGroup: EventLoopGroup,
    val server: SOCKSBean,
    val port: Int,
    val resolver: DnsNameResolver
) : AbstractInstance,
    ChannelInitializer<Channel>() {

    lateinit var channel: Channel
    val localPort get() = (channel.localAddress() as InetSocketAddress).port

    override fun launch() {
        channel = ServerBootstrap().group(eventLoopGroup)
            .channel(SagerNet.serverSocketChannel)
            .childHandler(this)
            .bind(port)
            .sync()
            .channel()
    }

    override fun destroy(scope: CoroutineScope) {
        if (::channel.isInitialized) channel.close()
    }

    override fun initChannel(ch: Channel) {
        ch.pipeline().addLast(Connection()).remove(this)
    }

    val localDNSPort = DataStore.localDNSPort

    inner class Connection : ChannelInboundHandlerAdapter() {

        val serverHandler = Socks4ProxyHandler(
            InetSocketAddress(
                server.finalAddress, server.finalPort
            ), server.username
        )

        lateinit var serverConnection: ChannelFuture
        val socks5InitialRequestDecoder = Socks5InitialRequestDecoder()
        val socks5CommandRequestDecoder = Socks5CommandRequestDecoder()

        override fun channelRegistered(ctx: ChannelHandlerContext) {
            ctx.channel().pipeline().addFirst(
                Socks5ServerEncoder.DEFAULT,
                socks5InitialRequestDecoder,
                socks5CommandRequestDecoder
            )
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            when (msg) {
                is Socks5InitialRequest -> {
                    ctx.writeAndFlush(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                    ctx.pipeline().remove(socks5InitialRequestDecoder)
                    ReferenceCountUtil.release(msg)
                }
                is Socks5CommandRequest -> {
                    if (msg.type() != Socks5CommandType.CONNECT) {
                        ctx.writeAndFlush(
                            DefaultSocks5CommandResponse(
                                Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.IPv4
                            )
                        )
                        ctx.close()
                        ReferenceCountUtil.release(msg)
                        return
                    }
                    val destAddress = when (msg.dstAddrType()) {
                        Socks5AddressType.IPv4 -> {
                            InetSocketAddress(msg.dstAddr(), msg.dstPort())
                        }
                        Socks5AddressType.DOMAIN -> {
                            if (server.protocol == 2) {
                                InetSocketAddress.createUnresolved(msg.dstAddr(), msg.dstPort())
                            } else {
                                val address = resolver.resolve(msg.dstAddr()).sync()
                                if (!address.isSuccess) {
                                    ctx.writeAndFlush(
                                        DefaultSocks5CommandResponse(
                                            Socks5CommandStatus.HOST_UNREACHABLE,
                                            Socks5AddressType.IPv4
                                        )
                                    )
                                    ctx.close()
                                    return
                                }
                                InetSocketAddress(address.get(), msg.dstPort())
                            }
                        }
                        else -> {
                            ctx.writeAndFlush(
                                DefaultSocks5CommandResponse(
                                    Socks5CommandStatus.NETWORK_UNREACHABLE, Socks5AddressType.IPv4
                                )
                            )
                            ctx.close()
                            ReferenceCountUtil.release(msg)
                            return
                        }
                    }

                    ctx.writeAndFlush(
                        DefaultSocks5CommandResponse(
                            Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4
                        )
                    )

                    ctx.pipeline()
                        .remove(socks5CommandRequestDecoder)
                        .remove(Socks5ServerEncoder.DEFAULT)

                    serverConnection = Bootstrap().group(eventLoopGroup)
                        .channel(SagerNet.socketChannel)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30 * 1000)
                        .handler(object : ChannelInboundHandlerAdapter() {
                            override fun channelRegistered(ctx: ChannelHandlerContext) {
                                ctx.channel().pipeline().addFirst(serverHandler)
                            }

                            override fun channelRead(serverCtx: ChannelHandlerContext, msg: Any) {
                                ctx.writeAndFlush(msg)
                            }

                            override fun channelInactive(serverCtx: ChannelHandlerContext) {
                                ctx.close()
                            }

                            override fun exceptionCaught(
                                serverCtx: ChannelHandlerContext, cause: Throwable
                            ) {
                                ctx.close()
                                Logs.w(cause.readableMessage)
                            }
                        })
                        .connect(destAddress)
                    ReferenceCountUtil.release(msg)
                }
                else -> {
                    if (::serverConnection.isInitialized) {
                        serverConnection.sync().channel().writeAndFlush(msg)
                    } else {
                        Logs.e("Unexpected message $msg")
                        ReferenceCountUtil.release(msg)
                    }
                }
            }
        }


        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            Logs.w(cause.readableMessage)
        }

    }

}
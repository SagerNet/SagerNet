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

package io.nekohasekai.sagernet.tun.netty

import io.nekohasekai.sagernet.tun.UdpForwarder
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.socksx.v5.*
import io.netty.handler.proxy.ProxyConnectException
import io.netty.util.concurrent.DefaultPromise
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GlobalEventExecutor
import okhttp3.internal.connection.RouteSelector.Companion.socketHost

class Socks5UdpClient(val udpSession: UdpForwarder.UdpSession) : ChannelInboundHandlerAdapter() {

    private val promise = DefaultPromise<Socks5CommandResponse>(GlobalEventExecutor.INSTANCE)
    val future: Future<Socks5CommandResponse> get() = promise

    override fun channelRead(ctx: ChannelHandlerContext, response: Any) {

        if (response is Socks5InitialResponse) {
            if (response.authMethod() !== Socks5AuthMethod.NO_AUTH) {
                throw ProxyConnectException("[Socks5UdpClient] unexpected authMethod: " + response.authMethod())
            }

            val destAddress = udpSession.destAddress
            ctx.writeAndFlush(
                DefaultSocks5CommandRequest(
                    Socks5CommandType.UDP_ASSOCIATE,
                    Socks5AddressType.IPv4,
                    destAddress.socketHost,
                    destAddress.port
                )
            ).addListener {
                if (!it.isSuccess) {
                    promise.tryFailure(it.cause())
                }
            }

            return
        }


        response as Socks5CommandResponse
        if (response.status() !== Socks5CommandStatus.SUCCESS) {
            val exception = ProxyConnectException("Status: " + response.status())
            promise.tryFailure(exception)
            throw exception
        }

        promise.setSuccess(response)
    }

}
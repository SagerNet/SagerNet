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

package io.nekohasekai.sagernet.tun.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.internal.ObjectUtil;

/**
 * Encodes a client-side {@link Socks5UdpMessage} into a {@link ByteBuf}.
 */
@Sharable
public class Socks5UdpMessageEncoder extends MessageToByteEncoder<Socks5UdpMessage> {

    public static final Socks5UdpMessageEncoder DEFAULT = new Socks5UdpMessageEncoder();

    private final Socks5AddressEncoder addressEncoder;

    /**
     * Creates a new instance with the default {@link Socks5AddressEncoder}.
     */
    protected Socks5UdpMessageEncoder() {
        this(Socks5AddressEncoder.DEFAULT);
    }

    /**
     * Creates a new instance with the specified {@link Socks5AddressEncoder}.
     */
    public Socks5UdpMessageEncoder(Socks5AddressEncoder addressEncoder) {
        this.addressEncoder = ObjectUtil.checkNotNull(addressEncoder, "addressEncoder");
    }

    /**
     * Returns the {@link Socks5AddressEncoder} of this encoder.
     */
    protected final Socks5AddressEncoder addressEncoder() {
        return addressEncoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks5UdpMessage msg, ByteBuf out) throws Exception {
        out.writeShort(0);
        out.writeByte(msg.frag());

        final Socks5AddressType dstAddrType = msg.dstAddrType();
        out.writeByte(dstAddrType.byteValue());
        addressEncoder.encodeAddress(dstAddrType, msg.dstAddr(), out);
        out.writeShort(msg.dstPort());

        out.writeBytes(msg.data());
    }

}

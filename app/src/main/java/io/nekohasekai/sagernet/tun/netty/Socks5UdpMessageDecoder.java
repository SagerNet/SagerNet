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

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.internal.ObjectUtil;

/**
 * Decodes a single {@link Socks5UdpMessage} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */
public class Socks5UdpMessageDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final Socks5AddressDecoder addressDecoder;

    public Socks5UdpMessageDecoder() {
        this(Socks5AddressDecoder.DEFAULT);
    }

    public Socks5UdpMessageDecoder(Socks5AddressDecoder addressDecoder) {
        super();
        this.addressDecoder = ObjectUtil.checkNotNull(addressDecoder, "addressDecoder");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        ByteBuf in = msg.content();
        final short rsv = in.readShort();
        if (rsv != 0) {
            throw new DecoderException("Invalid udp message without rsv = 0");
        }
        byte frag = in.readByte();
        final Socks5AddressType addrType = Socks5AddressType.valueOf(in.readByte());
        final String addr = addressDecoder.decodeAddress(addrType, in);
        final int port = in.readUnsignedShort();
        final ByteBuf data = Unpooled.buffer(in.readableBytes());
        in.readBytes(data);

        out.add(new DefaultSocks5UdpMessage(frag, addrType, addr, port, data));
    }

}

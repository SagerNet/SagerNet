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

import java.net.IDN;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.socksx.v5.AbstractSocks5Message;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.util.NetUtil;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link Socks5UdpMessage}.
 */
public final class DefaultSocks5UdpMessage extends AbstractSocks5Message implements Socks5UdpMessage {

    public byte frag;
    public Socks5AddressType dstAddrType;
    public String dstAddr;
    public int dstPort;
    public ByteBuf data;

    public DefaultSocks5UdpMessage(
            byte frag, Socks5AddressType dstAddrType, String dstAddr, int dstPort, ByteBuf data) {

        this.frag = frag;
        this.dstAddrType = dstAddrType;
        this.dstAddr = dstAddr;
        this.dstPort = dstPort;
        this.data = data;
    }

    @Override
    public byte frag() {
        return frag;
    }

    @Override
    public Socks5AddressType dstAddrType() {
        return dstAddrType;
    }

    @Override
    public String dstAddr() {
        return dstAddr;
    }

    @Override
    public int dstPort() {
        return dstPort;
    }

    @Override
    public ByteBuf data() {
        return data;
    }

    @Override
    public String toString() {
        return "DefaultSocks5UdpMessage{" +
                "frag=" + frag +
                ", dstAddrType=" + dstAddrType +
                ", dstAddr='" + dstAddr + '\'' +
                ", dstPort=" + dstPort +
                ", data=" + data +
                '}';
    }
}

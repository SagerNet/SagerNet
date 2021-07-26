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

package io.nekohasekai.sagernet.tun.ip.ipv6;

import java.net.InetAddress;
import java.util.Arrays;

import cn.hutool.core.util.ArrayUtil;
import io.nekohasekai.sagernet.ktx.NetsKt;
import io.nekohasekai.sagernet.tun.ip.IPHeader;
import io.nekohasekai.sagernet.tun.ip.NetUtils;

public class IPv6Header extends IPHeader {

    public static final int OFFSET_PAYLOAD_LEN = 4;
    public static final int OFFSET_NEXT_HEADER = 6;
    public static final int OFFSET_HOP_LIMIT = 7;
    public static final int OFFSET_SRC_IP = 8;
    public static final int OFFSET_DST_IP = 24;
    public static final int OFFSET_HEADER = 40;

    public IPv6Header(byte[] packet) {
        super(packet, 0, packet.length);
    }

    public IPv6Header(byte[] packet, int length) {
        super(packet, 0, length);
    }

    @Override
    public int getVersion() {
        return 6;
    }

    public int getPayloadLength() {
        return readShort(offset + OFFSET_PAYLOAD_LEN);
    }

    public void setPayloadLength(int length) {
        writeShort((short) length, offset + OFFSET_PAYLOAD_LEN);
    }

    public int getNextHeader() {
        return readInt8(offset + OFFSET_NEXT_HEADER);
    }

    @Override
    public InetAddress getSourceAddress() {
        return NetsKt.parseAddress(ArrayUtil.sub(packet, offset + OFFSET_SRC_IP, offset + OFFSET_SRC_IP + 16));
    }

    @Override
    public void setSourceAddress(InetAddress address) {
        System.arraycopy(address.getAddress(), 0, packet, offset + OFFSET_SRC_IP, 16);
    }

    @Override
    public InetAddress getDestinationAddress() {
        return NetsKt.parseAddress(ArrayUtil.sub(packet, offset + OFFSET_DST_IP, offset + OFFSET_DST_IP + 16));
    }

    @Override
    public void setDestinationAddress(InetAddress address) {
        System.arraycopy(address.getAddress(), 0, packet, offset + OFFSET_DST_IP, 16);
    }

    @Override
    public void revertAddress() {
        byte[] srcIp = new byte[16];
        System.arraycopy(packet, offset + OFFSET_SRC_IP, srcIp, 0, 16);
        System.arraycopy(packet, offset + OFFSET_DST_IP, packet, offset + OFFSET_SRC_IP, 16);
        System.arraycopy(srcIp, 0, packet, offset + OFFSET_DST_IP, 16);
    }

    @Override
    public int getHeaderLength() {
        getProtocol();
        return headerOffset;
    }

    private transient int headerOffset;
    private transient int protocol;

    @Override
    public int getProtocol() {
        if (headerOffset != 0) {
            return protocol;
        }
        headerOffset = OFFSET_HEADER;
        int nextHeader = getNextHeader();
        int maxHeader = packetLength - offset - getPayloadLength();
        while (headerOffset < maxHeader && nextHeader != NetUtils.IPPROTO_ICMPv6 && nextHeader != NetUtils.IPPROTO_TCP && nextHeader != NetUtils.IPPROTO_UDP) {
            nextHeader = readInt8(headerOffset);
            int optDataLen = readInt8(headerOffset + 1);
            headerOffset += 2 + optDataLen;
        }
        protocol = nextHeader;
        return protocol;
    }

    @Override
    public long getIPChecksum() {
        // length 32 = src ip(16) + dest ip(16)
        return getSum(offset + OFFSET_SRC_IP, 32);
    }

    @Override
    public void updateChecksum() {
        // no checksum field in ipv6 header
    }

    @Override
    public int getDataLength() {
        return packetLength - getHeaderLength();
    }

    @Override
    public IPHeader copyOf() {
        return new IPv6Header(Arrays.copyOf(packet, packetLength));
    }

}

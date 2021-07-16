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
package io.nekohasekai.sagernet.tun.ip;

import androidx.annotation.NonNull;

import java.util.Locale;

import cn.hutool.core.util.ArrayUtil;

/**
 * The UDP module  must be able to determine the source and destination internet addresses and
 * the protocol field from the internet header.
 * <p>
 * UDP Header Format:
 * <p>
 * 0      7 8     15 16    23 24    31
 * +--------+--------+--------+--------+
 * |     Source      |   Destination   |
 * |      Port       |      Port       |
 * +--------+--------+--------+--------+
 * |                 |                 |
 * |     Length      |    Checksum     |
 * +--------+--------+--------+--------+
 * |
 * |          data octets ...
 * +---------------- ...
 * <p>
 * See https://tools.ietf.org/html/rfc768
 *
 * @author Megatron King
 * @since 2018-10-10 23:04
 */
public class UDPHeader extends Header {

    private static final short OFFSET_SRC_PORT = 0;
    private static final short OFFSET_DEST_PORT = 2;
    private static final short OFFSET_TLEN = 4;
    private static final short OFFSET_CRC = 6;

    private final IPHeader ipHeader;

    public UDPHeader(IPHeader header) {
        super(header.packet, header.getHeaderLength());
        ipHeader = header;
    }

    public IPHeader getIpHeader() {
        return ipHeader;
    }

    public short getSourcePort() {
        return readShort(offset + OFFSET_SRC_PORT);
    }

    public void setSourcePort(short port) {
        writeShort(port, offset + OFFSET_SRC_PORT);
    }

    public short getDestinationPort() {
        return readShort(offset + OFFSET_DEST_PORT);
    }

    public void setDestinationPort(short port) {
        writeShort(port, offset + OFFSET_DEST_PORT);
    }

    public void revertPort() {
        byte[] srcIp = ArrayUtil.sub(packet, offset + OFFSET_SRC_PORT, offset + OFFSET_SRC_PORT + 2);
        System.arraycopy(packet, offset + OFFSET_DEST_PORT, packet, offset + OFFSET_SRC_PORT, 2);
        System.arraycopy(srcIp, 0, packet, offset + OFFSET_DEST_PORT, 2);
    }

    public short getCrc() {
        return readShort(offset + OFFSET_CRC);
    }

    public void setCrc(short crc) {
        writeShort(crc, offset + OFFSET_CRC);
    }

    public int getHeaderLength() {
        return 8;
    }

    public int getTotalLength() {
        return readShort(offset + OFFSET_TLEN);
    }

    public void setTotalLength(short len) {
        writeShort(len, offset + OFFSET_TLEN);
    }

    public void updateChecksum() {
        setCrc((short) 0);
        setCrc(computeChecksum());
    }

    private short computeChecksum() {
        // Sum = Ip Sum(Source Address + Destination Address) + Protocol + UDP Length
        // Checksum is the 16-bit one's complement of the one's complement sum of a
        // pseudo header of information from the IP header, the UDP header, and the
        // data,  padded  with zero octets  at the end (if  necessary)  to  make  a
        // multiple of two octets.
        int dataLength = ipHeader.getDataLength();
        long sum = ipHeader.getIPChecksum();
        sum += ipHeader.getProtocol() & 0xFF;
        sum += dataLength;
        sum += getSum(offset, dataLength);
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (short) ~sum;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%d -> %d", getSourcePort() & 0xFFFF,
                getDestinationPort() & 0xFFFF);
    }

    public UDPHeader copy() {
        return new UDPHeader(ipHeader.copyOf());
    }

    public byte[] header() {
        byte[] data = new byte[ipHeader.getHeaderLength() + getHeaderLength()];
        System.arraycopy(packet, 0, data, 0, data.length);
        return data;
    }

    public byte[] data() {
        int size = ipHeader.getDataLength() - getHeaderLength();
        int dataOffset = ipHeader.getHeaderLength() + getHeaderLength();
        byte[] data = new byte[size];
        System.arraycopy(packet, dataOffset, data, 0, size);
        return data;
    }

}

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
package io.nekohasekai.sagernet.tun.ip.ipv4;

import io.nekohasekai.sagernet.tun.ip.Header;

/**
 * ICMP messages are sent using the basic IP header. The first octet of the data portion of the
 * datagram is a ICMP type field; the value of this field determines the format of the remaining
 * data. Any field labeled "unused" is reserved for later extensions and must be zero when sent,
 * but receivers should not use these fields (except to include them in the checksum).
 * Unless otherwise noted under the individual format descriptions, the values of the internet
 * header fields are as follows:
 * <p>
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |     Type      |     Code      |          Checksum             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                              TBD                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                            Optional                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * <p>
 * See https://tools.ietf.org/html/rfc792
 *
 * @author Megatron King
 * @since 2018-10-10 23:04
 */
public class ICMPHeader extends Header {

    private static final short OFFSET_TYPE = 0;
    private static final short OFFSET_CODE = 1;
    private static final short OFFSET_CRC = 2;

    public final IPv4Header ipHeader;

    public ICMPHeader(IPv4Header header) {
        super(header.packet, header.getHeaderLength());
        ipHeader = header;
    }


    public byte getType() {
        return readByte(offset + OFFSET_TYPE);
    }

    public void setType(byte type) {
        writeInt8(type, offset + OFFSET_TYPE);
    }

    public byte getCode() {
        return readByte(offset + OFFSET_CODE);
    }

    public short getCrc() {
        return readShort(offset + OFFSET_CRC);
    }

    public void setCrc(short crc) {
        writeShort(crc, offset + OFFSET_CRC);
    }

    public void updateChecksum() {
        setCrc((short) 0);
        setCrc(computeChecksum());
    }

    public void revertEcho() {
        packet[offset + OFFSET_TYPE] = 0;
        int crc = packet[offset + OFFSET_CRC] & 0xFF;
        if (crc >= 247) {
            packet[offset + OFFSET_CRC] = (byte) (crc - 248);
            packet[offset + OFFSET_CRC + 1] = (byte) ((packet[offset + OFFSET_CRC] & 0xFF) + 1);
        } else {
            packet[offset + OFFSET_CRC] = (byte) (crc + 8);
        }
    }

    private short computeChecksum() {
        int dataLength = 0;
        int sum = 0;
        sum += getSum(offset, ipHeader.getDataLength());
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (short) ~sum;
    }

}

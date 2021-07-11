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

import java.util.Locale;

import io.nekohasekai.sagernet.tun.ip.ipv4.IPv4Header;

/**
 * TCP segments are sent as internet datagrams. The Internet Protocol header carries several
 * information fields, including the source and destination host addresses. A TCP header follows
 * the internet header, supplying information specific to the TCP protocol. This division allows
 * for the existence of host level protocols other than TCP.
 *
 * TCP Header Format:
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          Source Port          |       Destination Port        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Sequence Number                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Acknowledgment Number                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Data |           |U|A|P|R|S|F|                               |
 * | Offset| Reserved  |R|C|S|S|Y|I|            Window             |
 * |       |           |G|K|H|T|N|N|                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           Checksum            |         Urgent Pointer        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Options                    |    Padding    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             data                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * See https://tools.ietf.org/html/rfc793#section-3.1
 *
 * @author Megatron King
 * @since 2018-10-10 22:19
 */
public class TCPHeader extends Header {

    private static final int OFFSET_SRC_PORT = 0;
    private static final int OFFSET_DEST_PORT = 2;
    private static final int OFFSET_LENRES = 12;
    private static final int OFFSET_CRC = 16;
    private static final int OFFSET_FLAG = 13;
    private static final int OFFSET_SEQ = 4;
    private static final int OFFSET_ACK = 8;

    private static final int FIN = 1;
    private static final int SYN = 2;
    private static final int RST = 4;
    private static final int PSH = 8;
    private static final int ACK = 16;
    private static final int URG = 32;

    private IPHeader ipHeader;

    public TCPHeader(IPHeader header, byte[] packet, int offset) {
        super(packet, offset);
        ipHeader = header;
    }

    public void updateOffset(int offset) {
        this.offset = offset;
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

    public int getHeaderLength() {
        int lenres = packet[offset + OFFSET_LENRES] & 0xFF;
        return (lenres >> 4) * 4;
    }

    public short getCrc() {
        return readShort(offset + OFFSET_CRC);
    }

    public void setCrc(short crc) {
        writeShort(crc, offset + OFFSET_CRC);
    }

    public byte getFlag() {
        return packet[offset + OFFSET_FLAG];
    }

    public int getSeqID() {
        return readInt(offset + OFFSET_SEQ);
    }

    public int getAckID() {
        return readInt(offset + OFFSET_ACK);
    }

    public void updateChecksum() {
        setCrc((short) 0);
        setCrc(computeChecksum());
    }

    private short computeChecksum() {
        // Sum = Ip Sum(Source Address + Destination Address) + Protocol + TCP Length
        // The checksum field is the 16 bit one's complement of the one's complement sum of all 16
        // bit words in the header and text.
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

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s%s%s%s%s%s %d -> %d %s:%s",
                (getFlag() & SYN) == SYN ? "SYN" : "",
                (getFlag() & ACK) == ACK ? "ACK" : "",
                (getFlag() & PSH) == PSH ? "PSH" : "",
                (getFlag() & RST) == RST ? "RST" : "",
                (getFlag() & FIN) == FIN ? "FIN" : "",
                (getFlag() & URG) == URG ? "URG" : "",
                getSourcePort() & 0xFFFF,
                getDestinationPort() & 0xFFFF,
                getSeqID(),
                getAckID());
    }

}

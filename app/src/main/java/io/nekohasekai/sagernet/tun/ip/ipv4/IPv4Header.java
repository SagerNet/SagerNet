/*  NetBare - An android network capture and injection library.
 *  Copyright (C) 2018-2019 Megatron King
 *  Copyright (C) 2018-2019 GuoShi
 *
 *  NetBare is free software: you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Found-
 *  ation, either version 3 of the License, or (at your option) any later version.
 *
 *  NetBare is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 *  PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with NetBare.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package io.nekohasekai.sagernet.tun.ip.ipv4;

import java.net.InetAddress;
import java.util.Arrays;

import cn.hutool.core.util.ArrayUtil;
import io.nekohasekai.sagernet.ktx.NetsKt;
import io.nekohasekai.sagernet.tun.ip.IPHeader;

/**
 * A summary of the contents of the internet header follows:
 * <p>
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |Version|  IHL  |Type of Service|          Total Length         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Identification        |Flags|      Fragment Offset    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Time to Live |    Protocol   |         Header Checksum       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                       Source Address                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Destination Address                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Options                    |    Padding    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * <p>
 * See https://tools.ietf.org/html/rfc791#section-3.1
 *
 * @author Megatron King
 * @since 2018-10-10 21:02
 */
public final class IPv4Header extends IPHeader {

    public static final int MIN_HEADER_LENGTH = 20;

    private static final int OFFSET_PROTOCOL = 9;
    private static final int OFFSET_VER_IHL = 0;
    private static final int OFFSET_SRC_IP = 12;
    private static final int OFFSET_DEST_IP = 16;
    private static final int OFFSET_TLEN = 2;
    private static final int OFFSET_CRC = 10;

    public IPv4Header(byte[] packet) {
        super(packet, 0, packet.length);
    }

    public IPv4Header(byte[] packet, int length) {
        super(packet, 0, length);
    }

    @Override
    public int getVersion() {
        return 4;
    }

    @Override
    public int getProtocol() {
        return packet[offset + OFFSET_PROTOCOL];
    }

    @Override
    public int getHeaderLength() {
        return (packet[offset + OFFSET_VER_IHL] & 0x0F) * 4;
    }

    public void setHeaderLength(int value) {
        packet[offset + OFFSET_VER_IHL] = (byte) ((4 << 4) | (value / 4));
    }

    public int getSourceIP() {
        return readInt(offset + OFFSET_SRC_IP);
    }

    public void setSourceIP(int ip) {
        writeInt(ip, offset + OFFSET_SRC_IP);
    }

    public int getDestinationIP() {
        return readInt(offset + OFFSET_DEST_IP);
    }

    public void setDestinationIP(int ip) {
        writeInt(ip, offset + OFFSET_DEST_IP);
    }

    @Override
    public void revertAddress() {
        byte[] srcIp = new byte[4];
        System.arraycopy(packet, offset + OFFSET_SRC_IP, srcIp, 0, 4);
        System.arraycopy(packet, offset + OFFSET_DEST_IP, packet, offset + OFFSET_SRC_IP, 4);
        System.arraycopy(srcIp, 0, packet, offset + OFFSET_DEST_IP, 4);
    }

    @Override
    public InetAddress getSourceAddress() {
        return NetsKt.parseAddress(ArrayUtil.sub(packet, offset + OFFSET_SRC_IP, offset + OFFSET_SRC_IP + 4));
    }

    @Override
    public void setSourceAddress(InetAddress address) {
        System.arraycopy(address.getAddress(), 0, packet, offset + OFFSET_SRC_IP, 4);
    }

    @Override
    public InetAddress getDestinationAddress() {
        return NetsKt.parseAddress(ArrayUtil.sub(packet, offset + OFFSET_DEST_IP, offset + OFFSET_DEST_IP + 4));
    }

    @Override
    public void setDestinationAddress(InetAddress address) {
        System.arraycopy(address.getAddress(), 0, packet, offset + OFFSET_DEST_IP, 4);
    }

    @Override
    public int getDataLength() {
        return this.getTotalLength() - this.getHeaderLength();
    }

    public int getTotalLength() {
        return readShort(offset + OFFSET_TLEN) & 0xFFFF;
    }

    public void setTotalLength(short len) {
        writeShort(len, offset + OFFSET_TLEN);
        packetLength = len;
    }

    public short getCrc() {
        return readShort(offset + OFFSET_CRC);
    }

    public void setCrc(short crc) {
        writeShort(crc, offset + OFFSET_CRC);
    }

    @Override
    public void updateChecksum() {
        setCrc((short) 0);
        setCrc(computeChecksum());
    }

    @Override
    public long getIPChecksum() {
        // length 8 = src ip(4) + dest ip(4)
        return getSum(offset + OFFSET_SRC_IP, 8);
    }

    private short computeChecksum() {
        // The checksum field is the 16 bit one's complement of the one's complement sum of all
        // 16 bit words in the header.
        int len = getHeaderLength();
        long sum = getSum(offset, len);
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (short) ~sum;
    }

    @Override
    public IPHeader copyOf() {
        return new IPv4Header(Arrays.copyOf(packet, packetLength));
    }

}

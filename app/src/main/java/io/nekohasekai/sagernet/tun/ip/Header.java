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
package io.nekohasekai.sagernet.tun.ip;

/**
 * An abstract header object for ip protocol packets, provides some common apis.
 *
 * @author Megatron King
 * @since 2018-10-09 16:28
 */
public abstract class Header {

    public byte[] packet;
    public int offset;

    public Header(byte[] packet, int offset) {
        this.packet = packet;
        this.offset = offset;
    }

    protected byte readByte(int offset) {
        return packet[offset];
    }

    protected void writeByte(byte value, int offset) {
        packet[offset] = value;
    }

    protected int readInt4(int offset, boolean suffix) {
        return !suffix ?
                packet[offset] >>> 4 :
                packet[offset] << 4 >>> 4;
    }

    protected int readInt8(int offset) {
        return packet[offset] & 0xFF;
    }

    protected void writeInt8(int value, int offset) {
        packet[offset] = (byte) value;
    }

    protected short readShort(int offset) {
        int r = ((packet[offset] & 0xFF) << 8) | (packet[offset + 1] & 0xFF);
        return (short) r;
    }

    protected void writeShort(short value, int offset) {
        packet[offset] = (byte) (value >> 8);
        packet[offset + 1] = (byte) (value);
    }

    protected int readInt(int offset) {
        return ((packet[offset] & 0xFF) << 24)
                | ((packet[offset + 1] & 0xFF) << 16)
                | ((packet[offset + 2] & 0xFF) << 8)
                | (packet[offset + 3] & 0xFF);
    }

    protected void writeInt(int value, int offset) {
        packet[offset] = (byte) (value >> 24);
        packet[offset + 1] = (byte) (value >> 16);
        packet[offset + 2] = (byte) (value >> 8);
        packet[offset + 3] = (byte) value;
    }

    protected long getSum(int offset, int len) {
        long sum = 0;
        while (len > 1) {
            sum += readShort(offset) & 0xFFFF;
            offset += 2;
            len -= 2;
        }

        if (len > 0) {
            sum += (packet[offset] & 0xFF) << 8;
        }
        return sum;
    }

}

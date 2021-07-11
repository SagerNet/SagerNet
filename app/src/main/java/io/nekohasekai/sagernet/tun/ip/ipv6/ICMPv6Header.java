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

import io.nekohasekai.sagernet.tun.ip.Header;

public class ICMPv6Header extends Header {

    private static final short OFFSET_TYPE = 0;
    private static final short OFFSET_CODE = 1;
    private static final short OFFSET_CRC = 2;
    private static final short OFFSET_AID = 4;
    private static final short OFFSET_SEQ = 6;
    private static final short OFFSET_DATA = 8;

    private final IPv6Header ipHeader;

    public ICMPv6Header(IPv6Header header) {
        super(header.packet, header.getHeaderLength());
        ipHeader = header;
    }


    public int getType() {
        return readInt8(offset + OFFSET_TYPE);
    }

    public void setType(int type) {
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

    public void revertEcho() {
        packet[offset + OFFSET_TYPE] = (byte) 129;
        int crc = packet[offset + OFFSET_CRC] & 0xFF;
        if (crc == 0) {
            packet[offset + OFFSET_CRC] = (byte) 255;
            packet[offset + OFFSET_CRC + 1] = (byte) ((packet[offset + OFFSET_CRC] & 0xFF) - 1);
        } else {
            packet[offset + OFFSET_CRC] = (byte) (crc - 1);
        }
    }

}

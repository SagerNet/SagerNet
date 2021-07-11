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

import static io.netty.buffer.ByteBufUtil.appendPrettyHexDump;
import static io.netty.util.internal.StringUtil.NEWLINE;

import java.net.Inet4Address;
import java.net.InetAddress;

import io.nekohasekai.sagernet.bg.VpnService;
import io.netty.buffer.Unpooled;

public class NetUtils {

    public static final int IPPROTO_ICMP = 1;
    public static final int IPPROTO_ICMPv6 = 58;

    public static final int IPPROTO_TCP = 6;
    public static final int IPPROTO_UDP = 17;

    public static String formatByteArray(String prefix, byte[] byteArray) {
        int length = byteArray.length;
        if (length == 0) {
            return prefix + ": 0B";
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append(prefix).append(": ").append(length).append('B');
            buf.append(NEWLINE);
            appendPrettyHexDump(buf, Unpooled.wrappedBuffer(byteArray));
            return buf.toString();
        }
    }

}

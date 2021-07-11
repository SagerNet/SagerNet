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

package io.nekohasekai.sagernet.tun

import java.io.File
import java.util.regex.Pattern

object UidDumperLegacy {

    val TCP_IPV4_PROC = File("/proc/net/tcp")
    val TCP_IPV6_PROC = File("/proc/net/tcp6")
    val UDP_IPV4_PROC = File("/proc/net/udp")
    val UDP6_IPV6_PROC = File("/proc/net/udp6")

    val IPV4_PATTERN = Pattern.compile(
        "\\s+\\d+:\\s([0-9A-F]{8}):" + "([0-9A-F]{4})\\s([0-9A-F]{8}):([0-9A-F]{4})\\s([0-9A-F]{2})\\s[0-9A-F]{8}:[0-9A-F]{8}" + "\\s[0-9A-F]{2}:[0-9A-F]{8}\\s[0-9A-F]{8}\\s+([0-9A-F]+)",
        (Pattern.CASE_INSENSITIVE or Pattern.UNIX_LINES)
    )
    val IPV6_PATTERN = Pattern.compile(
        ("\\s+\\d+:\\s([0-9A-F]{32}):" + "([0-9A-F]{4})\\s([0-9A-F]{32}):([0-9A-F]{4})\\s([0-9A-F]{2})\\s[0-9A-F]{8}:[0-9A-F]{8}" + "\\s[0-9A-F]{2}:[0-9A-F]{8}\\s[0-9A-F]{8}\\s+([0-9A-F]+)"),
        (Pattern.CASE_INSENSITIVE or Pattern.UNIX_LINES)
    )

    fun dumpUid(proc: File, pattern: Pattern, port: Int): Int {
        for (line in proc.readLines()) {
            val matcher = pattern.matcher(line)
            while (matcher.find()) {
                val localPort = matcher.group(2)?.toIntOrNull(16) ?: continue
                if (localPort != port) continue
                return matcher.group(6)?.toIntOrNull() ?: continue
            }
        }
        return -1
    }

}

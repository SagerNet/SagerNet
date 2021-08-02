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

package io.nekohasekai.sagernet.tun.ip

class DirectICMPHeader(val ipHeader: DirectIPHeader) : DirectHeader(
    ipHeader.buffer, ipHeader.headerLength
) {

    var type by int8(OFFSET_TYPE)
    var code by int8(OFFSET_CODE)
    var checksum by int16(OFFSET_CHECKSUM)

    fun revertEcho() {
        buffer.setByte(offset + OFFSET_TYPE, 0)
        val checksum0 = buffer.getUnsignedByte(offset + OFFSET_CHECKSUM)
        if (checksum0 >= 247) {
            buffer.setByte(offset + OFFSET_CHECKSUM, checksum0 - 248)
            buffer.setByte(
                offset + OFFSET_CHECKSUM + 1, buffer.getUnsignedByte(offset + OFFSET_CHECKSUM) + 1
            )
        } else {
            buffer.setByte(offset + OFFSET_CHECKSUM, checksum0 + 8)
        }
    }

    fun updateChecksum() {
        checksum = 0
        checksum = ipHeader.finishSum(ipHeader.readSum(offset, ipHeader.dataLength))
    }

    companion object {
        private const val OFFSET_TYPE = 0
        private const val OFFSET_CODE = 1
        private const val OFFSET_CHECKSUM = 2
    }

}
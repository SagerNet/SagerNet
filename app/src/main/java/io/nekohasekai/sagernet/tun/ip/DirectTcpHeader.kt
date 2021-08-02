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

class DirectTcpHeader(val ipHeader: DirectIPHeader) : DirectHeader(
    ipHeader.buffer, ipHeader.headerLength
) {

    var sourcePort by int16(OFFSET_SOURCE_PORT)
    var destinationPort by int16(OFFSET_DESTINATION_PORT)
    var checksum by int16(OFFSET_CHECKSUM)

    fun revertPort() {
        val sourcePort = buffer.copy(offset + OFFSET_SOURCE_PORT, 2)
        buffer.setBytes(
            offset + OFFSET_SOURCE_PORT, buffer.slice(offset + OFFSET_DESTINATION_PORT, 2)
        )
        buffer.setBytes(offset + OFFSET_DESTINATION_PORT, sourcePort)
    }

    var headerLength by field(OFFSET_DATA_OFFSET, {
        (buffer.getUnsignedByte(it).toInt() ushr 4) * 4
    }, { index, value ->
        buffer.setByte(
            index,
            ((value * 4) ushr 4 shl 4) or (buffer.getUnsignedByte(index).toInt() shl 4 ushr 4)
        )
    })

    fun updateChecksum() {
        checksum = 0
        val dataLength = ipHeader.dataLength
        var sum = ipHeader.getAddressSum()
        sum += ipHeader.protocol.toLong()
        sum += dataLength.toLong()
        sum += ipHeader.readSum(offset, dataLength)
        checksum = ipHeader.finishSum(sum)
    }

    companion object {

        private const val OFFSET_SOURCE_PORT = 0
        private const val OFFSET_DESTINATION_PORT = 2
        private const val OFFSET_DATA_OFFSET = 12
        private const val OFFSET_CHECKSUM = 16


    }
}
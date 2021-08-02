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

import io.netty.buffer.ByteBuf

// https://datatracker.ietf.org/doc/html/rfc791

class DirectIPv4Header(buffer: ByteBuf, packetLength: Int) : DirectIPHeader(buffer, packetLength) {

    override var version by int4Left(OFFSET_VERSION_IHL)
    override var headerLength by field(OFFSET_VERSION_IHL,
        { (buffer.getUnsignedByte(it).toInt() and 0x0F) * 4 },
        { index, value -> buffer.setByte(index, 4 shl 4 or value / 4) })

    override var protocol by int8(OFFSET_PROTOCOL)
    override var sourceAddress by byteArray(OFFSET_SOURCE_ADDRESS, 4)
    override var destinationAddress by byteArray(OFFSET_DESTINATION_ADDRESS, 4)

    var checksum by int16(OFFSET_CHECKSUM)
    var totalLength by int16(OFFSET_TOTAL_LENGTH)

    override val dataLength: Int
        get() = packetLength - headerLength

    override fun revertAddress() {
        val sourceAddress = buffer.copy(offset + OFFSET_SOURCE_ADDRESS, 4)
        buffer.setBytes(
            OFFSET_SOURCE_ADDRESS, buffer.slice(
                offset + OFFSET_DESTINATION_ADDRESS, 4
            )
        )
        buffer.setBytes(OFFSET_DESTINATION_ADDRESS, sourceAddress)
        sourceAddress.release()
    }

    override fun updateChecksum() {
        checksum = 0
        checksum = finishSum(readSum(offset, headerLength))
    }

    override fun getAddressSum(): Long {
        return readSum(offset + OFFSET_SOURCE_ADDRESS, 8)
    }

    override fun newInstance(buffer: ByteBuf) = DirectIPv4Header(buffer, packetLength)

    companion object {

        private const val OFFSET_VERSION_IHL = 0
        private const val OFFSET_TOTAL_LENGTH = 2
        private const val OFFSET_PROTOCOL = 9
        private const val OFFSET_CHECKSUM = 10
        private const val OFFSET_SOURCE_ADDRESS = 12
        private const val OFFSET_DESTINATION_ADDRESS = 16

    }

}
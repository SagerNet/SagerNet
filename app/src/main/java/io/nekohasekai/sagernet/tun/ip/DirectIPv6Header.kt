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

import io.nekohasekai.sagernet.ktx.IPPROTO_ICMPv6
import io.nekohasekai.sagernet.ktx.IPPROTO_TCP
import io.nekohasekai.sagernet.ktx.IPPROTO_UDP
import io.netty.buffer.ByteBuf

// https://datatracker.ietf.org/doc/html/rfc8200

class DirectIPv6Header(buffer: ByteBuf, packetLength: Int) : DirectIPHeader(buffer, packetLength) {

    override var version by int4Left(OFFSET_VERSION)

    override var sourceAddress by byteArray(OFFSET_SOURCE_ADDRESS, 16)
    override var destinationAddress by byteArray(OFFSET_DESTINATION_ADDRESS, 16)
    override var dataLength by int16(OFFSET_PAYLOAD_LENGTH)
    var nextHeader by int8(OFFSET_NEXT_HEADER)
    override val headerLength: Int
        get() {
            protocol
            return headerOffset
        }

    override fun revertAddress() {
        val sourceAddress = buffer.copy(offset + OFFSET_SOURCE_ADDRESS, 16)
        buffer.setBytes(
            OFFSET_SOURCE_ADDRESS, buffer.slice(
                offset + OFFSET_DESTINATION_ADDRESS, 16
            )
        )
        buffer.setBytes(OFFSET_DESTINATION_ADDRESS, sourceAddress)
    }

    private var headerOffset = 0
    private var _protocol = 0
    override val protocol: Int
        get() {
            if (headerOffset != 0) {
                return _protocol
            }
            headerOffset = OFFSET_HEADER
            var nextHeader = nextHeader
            val maxHeader = packetLength - offset - dataLength
            while (headerOffset < maxHeader && nextHeader != IPPROTO_ICMPv6 && nextHeader != IPPROTO_TCP && nextHeader != IPPROTO_UDP) {
                nextHeader = buffer.getUnsignedByte(headerOffset).toInt()
                val optDataLen = buffer.getUnsignedByte(headerOffset + 1)
                headerOffset += 2 + optDataLen
            }
            _protocol = nextHeader
            return _protocol
        }

    override fun updateChecksum() {
        // no checksum
    }

    override fun getAddressSum(): Long {
        return readSum(offset + OFFSET_SOURCE_ADDRESS, 32)
    }

    override fun newInstance(buffer: ByteBuf) = DirectIPv6Header(buffer, packetLength)

    companion object {

        private const val OFFSET_VERSION = 0
        private const val OFFSET_PAYLOAD_LENGTH = 4
        private const val OFFSET_NEXT_HEADER = 6
        private const val OFFSET_SOURCE_ADDRESS = 8
        private const val OFFSET_DESTINATION_ADDRESS = 24
        private const val OFFSET_HEADER = 40

    }

}
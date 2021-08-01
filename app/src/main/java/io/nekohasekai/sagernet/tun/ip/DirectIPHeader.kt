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

import io.nekohasekai.sagernet.ktx.toByteArray
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.net.InetAddress
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

abstract class DirectIPHeader(buffer: ByteBuf, val packetLength: Int) : DirectHeader(buffer, 0) {

    abstract var version: Int
    abstract val protocol: Int
    abstract var sourceAddress: ByteBuf
    abstract var destinationAddress: ByteBuf

    abstract val headerLength: Int
    abstract val dataLength: Int

    internal fun readSum(position: Int, length: Int): Long {
        var index = position
        var sum = 0L
        repeat(length / 2) {
            sum += buffer.getUnsignedShort(index)
            index += 2
        }
        if (length % 2 > 0) {
            sum += buffer.getUnsignedByte(index).toLong() shl 8
        }
        return sum
    }

    internal fun finishSum(sum: Long): Int {
        var checksum = sum
        while (checksum shr 16 > 0) {
            checksum = (checksum and 0xFFFF) + (checksum shr 16)
        }
        return checksum.inv().toUShort().toInt()
    }

    var sourceInetAddress by InetAddressField(::sourceAddress)
    var destinationInetAddress by InetAddressField(::destinationAddress)

    abstract fun revertAddress()
    abstract fun updateChecksum()
    abstract fun getAddressSum(): Long
    abstract fun newInstance(buffer: ByteBuf): DirectIPHeader

    class InetAddressField(val field: KMutableProperty0<ByteBuf>) {
        operator fun getValue(thiz: Any, property: Any): InetAddress {
            return InetAddress.getByAddress(field.get().toByteArray())
        }

        operator fun setValue(thiz: Any, property: KProperty<*>, any: InetAddress) {
            field.set(Unpooled.wrappedBuffer(any.address))
        }
    }

}
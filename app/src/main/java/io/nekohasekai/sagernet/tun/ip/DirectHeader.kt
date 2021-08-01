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

import io.nekohasekai.sagernet.ktx.Logs
import io.netty.buffer.ByteBuf
import kotlin.reflect.KProperty

@Suppress("NOTHING_TO_INLINE")
abstract class DirectHeader(val buffer: ByteBuf, val offset: Int) {
    companion object {
        private const val BYTE_LENGTH = 8
    }

    internal fun <T> field(
        position: Int, get: (index: Int) -> T, set: (index: Int, value: T) -> Unit
    ) = Field(offset + position, get, set)

    internal fun int4Left(position: Int) = field(position, {
        buffer.getUnsignedByte(it).toInt() ushr 4
    }, { index, value ->
        buffer.setByte(
            index, (value ushr 4 shl 4) or (buffer.getUnsignedByte(index).toInt() shl 4 ushr 4)
        )
    })

    internal fun int8(position: Int) = field(position, { index ->
        buffer.getByte(index).toUByte().toInt()
    }, { index, value ->
        buffer.setByte(index, value)
    })

    internal fun int16(position: Int) = field(position, { index ->
        buffer.getUnsignedShort(index)
    }, { index, value ->
        buffer.setShort(index, value)
    })

    internal fun byteArray(position: Int, length: Int) = field(position,
        { index -> buffer.alloc().buffer(length).also { buffer.getBytes(index, it, length) } },
        { index, value -> buffer.setBytes(index, value) })

    internal class Field<T>(val position: Int, val get: (Int) -> T, val set: (Int, T) -> Unit) {
        operator fun getValue(thiz: DirectHeader, property: KProperty<*>): T {
            return get(position)
        }

        operator fun setValue(thiz: DirectHeader, property: KProperty<*>, value: T) {
            set(position, value)
        }
    }

}
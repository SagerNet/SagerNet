/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.ktx

import cn.hutool.core.lang.UUID
import cn.hutool.core.util.ArrayUtil
import libcore.Libcore
import java.io.ByteArrayOutputStream
import kotlin.experimental.and
import kotlin.experimental.or

fun uuid5(text: String): String {
    val data = ByteArrayOutputStream()
    data.write(ByteArray(16))
    data.write(text.toByteArray())
    val hash = Libcore.sha1(data.toByteArray())
    val result = ArrayUtil.sub(hash, 0, 16)
    result[6] = result[6] and 0x0F.toByte()
    result[6] = result[6] or 0x50.toByte()
    result[8] = result[8] and 0x3F.toByte()
    result[8] = result[8] or 0x80.toByte()
    var msb = 0L
    for (i in 0..7) {
        msb = msb shl 8 or (result[i].toLong() and 0xff)
    }
    var lsb = 0L
    for (i in 8..15) {
        lsb = lsb shl 8 or (result[i].toLong() and 0xff)
    }
    return UUID(msb, lsb).toString(false)
}
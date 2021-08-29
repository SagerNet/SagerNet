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

import android.os.Parcel
import android.os.Parcelable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import java.io.InputStream
import java.io.OutputStream


fun InputStream.byteBuffer() = ByteBufferInput(this)
fun OutputStream.byteBuffer() = ByteBufferOutput(this)

fun ByteBufferInput.readStringList(): List<String> {
    return mutableListOf<String>().apply {
        repeat(readInt()) {
            add(readString())
        }
    }
}

fun ByteBufferInput.readStringSet(): Set<String> {
    return linkedSetOf<String>().apply {
        repeat(readInt()) {
            add(readString())
        }
    }
}


fun ByteBufferOutput.writeStringList(list: List<String>) {
    writeInt(list.size)
    for (str in list) writeString(str)
}

fun ByteBufferOutput.writeStringList(list: Set<String>) {
    writeInt(list.size)
    for (str in list) writeString(str)
}

fun Parcelable.marshall(): ByteArray {
    val parcel = Parcel.obtain()
    writeToParcel(parcel, 0)
    val bytes = parcel.marshall()
    parcel.recycle()
    return bytes
}

fun ByteArray.unmarshall(): Parcel {
    val parcel = Parcel.obtain()
    parcel.unmarshall(this, 0, size)
    parcel.setDataPosition(0) // This is extremely important!
    return parcel
}

fun <T> ByteArray.unmarshall(constructor: (Parcel) -> T): T {
    val parcel = unmarshall()
    val result = constructor(parcel)
    parcel.recycle()
    return result
}
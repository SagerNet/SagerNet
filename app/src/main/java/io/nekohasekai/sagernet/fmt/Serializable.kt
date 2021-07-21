/******************************************************************************
 * *
 * Copyright (C) 2021 by nekohasekai <sekai></sekai>@neko.services>                    *
 * Copyright (C) 2021 by Max Lv <max.c.lv></max.c.lv>@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android></contact-shadowsocks-android>@mygod.be>  *
 * *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                       *
 * *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 * *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http:></http:>//www.gnu.org/licenses/>.       *
 * *
 */
package io.nekohasekai.sagernet.fmt

import android.os.Parcel
import android.os.Parcelable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput

abstract class Serializable : Parcelable {
    abstract fun initializeDefaultValues()
    abstract fun serializeToBuffer(output: ByteBufferOutput)
    abstract fun deserializeFromBuffer(input: ByteBufferInput)

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        val byteArray = KryoConverters.serialize(this)
        dest.writeInt(byteArray.size)
        dest.writeByteArray(byteArray)
    }

    abstract class CREATOR<T : Serializable> : Parcelable.Creator<T> {
        abstract fun newInstance(): T

        override fun createFromParcel(source: Parcel): T {
            val byteArray = ByteArray(source.readInt())
            source.readByteArray(byteArray)
            return KryoConverters.deserialize(newInstance(), byteArray)
        }
    }

}
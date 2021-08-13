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

package io.nekohasekai.sagernet.utils.unix

import android.os.ParcelFileDescriptor
import android.system.Os
import io.netty.channel.epoll.Epoll
import io.netty.channel.unix.FileDescriptor
import java.nio.ByteBuffer
import java.io.FileDescriptor as FD

class FileDescriptorCompact private constructor(impl: AbstractFileDescriptor) : AbstractFileDescriptor by impl {

    constructor(pfd: ParcelFileDescriptor) : this(
        if (Epoll.isAvailable()) NettyImplementation(pfd.fd) else OsImplementation(
            pfd.fileDescriptor
        )
    )

    private class NettyImplementation(fd: Int) : AbstractFileDescriptor {
        private val fileDescriptor = FileDescriptor(fd)

        override fun read(buffer: ByteBuffer, pos: Int, limit: Int): Int {
            return fileDescriptor.read(buffer, pos, limit)
        }

        override fun write(buffer: ByteBuffer, pos: Int, limit: Int): Int {
            return fileDescriptor.write(buffer, pos, limit)
        }
    }

    private class OsImplementation(private val fileDescriptor: FD) : AbstractFileDescriptor {

        override fun read(buffer: ByteBuffer, pos: Int, limit: Int): Int {
            buffer.position(pos).limit(limit)
            return Os.read(fileDescriptor, buffer)
        }

        override fun write(buffer: ByteBuffer, pos: Int, limit: Int): Int {
            buffer.position(pos).limit(limit)
            return Os.write(fileDescriptor, buffer)
        }
    }

}
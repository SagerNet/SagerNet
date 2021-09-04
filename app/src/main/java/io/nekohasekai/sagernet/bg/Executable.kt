/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.bg

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.text.TextUtils
import io.nekohasekai.sagernet.ktx.Logs
import java.io.File
import java.io.IOException

object Executable {
    const val SS_LOCAL = "libsslocal.so"
    const val SS_LIBEV_LOCAL = "libss-local.so"

    private val EXECUTABLES = setOf(
        SS_LOCAL,
        SS_LIBEV_LOCAL,
        "libtrojan.so",
        "libtrojan-go.so",
        "libnaive.so",
        "libbrook.so",
        "libhysteria.so",
        "libpingtunnel.so",
        "librelaybaton.so",
        "libwg.so"
    )

    fun killAll() {
        for (process in File("/proc").listFiles { _, name -> TextUtils.isDigitsOnly(name) }
            ?: return) {
            val exe = File(try {
                File(process, "cmdline").inputStream().bufferedReader().use {
                    it.readText()
                }
            } catch (_: IOException) {
                continue
            }.split(Character.MIN_VALUE, limit = 2).first())
            if (EXECUTABLES.contains(exe.name)) try {
                Os.kill(process.name.toInt(), OsConstants.SIGKILL)
                Logs.w("SIGKILL ${exe.nameWithoutExtension} (${process.name}) succeed")
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.ESRCH) {
                    Logs.w("SIGKILL ${exe.absolutePath} (${process.name}) failed")
                    Logs.w(e)
                }
            }
        }
    }
}

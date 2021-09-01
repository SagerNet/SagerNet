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

import android.util.Log
import cn.hutool.core.util.StrUtil
import java.io.InputStream
import java.io.OutputStream

object Logs {

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return StrUtil.subAfter(stackTrace[4].className, ".", true)
    }

    fun v(message: String) {
        //  if (BuildConfig.DEBUG) {
        Log.v(mkTag(), message)
//        }
    }

    fun v(message: String, exception: Throwable) {
        //  if (BuildConfig.DEBUG) {
        Log.v(mkTag(), message, exception)
//        }
    }

    fun d(message: String) {
        //  if (BuildConfig.DEBUG) {
        Log.d(mkTag(), message)
//        }
    }

    fun d(message: String, exception: Throwable) {
        //  if (BuildConfig.DEBUG) {
        Log.d(mkTag(), message, exception)
//        }
    }

    fun i(message: String) {
        Log.i(mkTag(), message)
    }

    fun i(message: String, exception: Throwable) {
        Log.i(mkTag(), message, exception)
    }

    fun w(message: String) {
        Log.w(mkTag(), message)
    }

    fun w(message: String, exception: Throwable) {
        Log.w(mkTag(), message, exception)
    }

    fun w(exception: Throwable) {
        Log.w(mkTag(), exception)
    }

    fun e(message: String) {
        Log.e(mkTag(), message)
    }

    fun e(message: String, exception: Throwable) {
        Log.e(mkTag(), message, exception)
    }

}

fun InputStream.use(out: OutputStream) {
    use { input ->
        out.use { output ->
            input.copyTo(output)
        }
    }
}
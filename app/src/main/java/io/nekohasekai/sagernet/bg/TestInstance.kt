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

package io.nekohasekai.sagernet.bg

import android.os.SystemClock
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.fmt.buildV2RayConfig
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

class TestInstance(profile: ProxyEntity) : V2RayInstance(profile) {

    private lateinit var continuation: Continuation<Int>
    private val httpPort = mkPort()

    override fun buildConfig() {
        config = buildV2RayConfig(profile, true, httpPort)
    }

    override var processes = GuardedProcessPool {
        Logs.w(it)
        continuation.tryResumeWithException(it)
    }

    suspend fun doTest(testTimes: Int): Int {
        return suspendCoroutine {
            continuation = it
            try {
                doTest0(testTimes)
            } catch (e: Throwable) {
                continuation.tryResumeWithException(e)
            }
        }
    }

    private fun destroy() {
        runBlocking { onMainDispatcher { destroy(this) } }
    }

    private fun doTest0(testTimes: Int) {
        init()
        launch()
        val timeout = Duration.ofSeconds(5L)
        val okHttpClient = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LOCALHOST, httpPort)))
            .connectTimeout(timeout)
            .callTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout)
            .build()

        fun newCall(times: Int) = okHttpClient.newCall(Request.Builder()
            .url(DataStore.connectionTestURL)
            .addHeader("Connection", if (times > 1) "keep-alive" else "close")
            .addHeader("User-Agent", "curl/7.74.0")
            .build())

        newCall(testTimes).enqueue(object : Callback {

            var times = testTimes
            var start = if (times == 1) SystemClock.elapsedRealtime() else 0L

            fun recall() {
                if (times < 2) start = SystemClock.elapsedRealtime()
                newCall(times).enqueue(this)
            }

            override fun onFailure(call: Call, e: IOException) {
                destroy()
                continuation.tryResumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {

                if (response.isSuccessful && times > 1) {
                    times--
                    recall()
                    return
                }

                val elapsed = SystemClock.elapsedRealtime() - start

                val code = response.code
                response.closeQuietly()
                destroy()

                if (code == 204 || code == 200) {
                    continuation.tryResume(elapsed.toInt())
                } else {
                    continuation.tryResumeWithException(IOException(app.getString(R.string.connection_test_error_status_code, code)))
                }

            }
        })

    }

}
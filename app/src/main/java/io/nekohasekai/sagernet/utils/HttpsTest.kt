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

package io.nekohasekai.sagernet.utils

import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.internal.closeQuietly
import java.io.IOException

/**
 * Based on: https://android.googlesource.com/platform/frameworks/base/+/b19a838/services/core/java/com/android/server/connectivity/NetworkMonitor.java#1071
 */
class HttpsTest : ViewModel() {
    sealed class Status {
        protected abstract val status: CharSequence
        open fun retrieve(setStatus: (CharSequence) -> Unit, errorCallback: (String) -> Unit) =
            setStatus(status)

        object Idle : Status() {
            override val status get() = app.getText(R.string.vpn_connected)
        }

        object Testing : Status() {
            override val status get() = app.getText(R.string.connection_test_testing)
        }

        class Success(private val elapsed: Long) : Status() {
            override val status
                get() = app.getString(if (DataStore.connectionTestURL.startsWith("https://")) {
                    R.string.connection_test_available
                } else {
                    R.string.connection_test_available_http
                }, elapsed)
        }

        sealed class Error : Status() {
            override val status get() = app.getText(R.string.connection_test_fail)
            protected abstract val error: String
            private var shown = false
            override fun retrieve(
                setStatus: (CharSequence) -> Unit,
                errorCallback: (String) -> Unit,
            ) {
                super.retrieve(setStatus, errorCallback)
                if (shown) return
                shown = true
                errorCallback(error)
            }

            class UnexpectedResponseCode(private val code: Int) : Error() {
                override val error
                    get() = app.getString(R.string.connection_test_error_status_code,
                        code)
            }

            class IOFailure(private val e: IOException) : Error() {
                override val error get() = app.getString(R.string.connection_test_error, e.message)
            }
        }

    }

    private var running: Call? = null
    val status = MutableLiveData<Status>(Status.Idle)
    val okHttpClient by lazy { OkHttpClient.Builder().proxy(requireProxy()).build() }

    fun testConnection() {
        cancelTest()
        status.value = Status.Testing

        runOnDefaultDispatcher {
            val start = SystemClock.elapsedRealtime()
            running = okHttpClient.newCall(
                Request.Builder()
                    .url(DataStore.connectionTestURL)
                    .addHeader("Connection", "close")
                    .addHeader("User-Agent", "curl/7.74.0")
                    .build()
            ).apply {
                val response = try {
                    execute()
                } catch (e: IOException) {
                    if (e.readableMessage.contains("failed to connect to /127.0.0.1") && e.readableMessage.contains("ECONNREFUSED")) {
                        delay(1000L)
                        onMainDispatcher {
                            testConnection()
                        }
                        return@runOnDefaultDispatcher
                    }
                    if (!isCanceled()) {
                        onMainDispatcher {
                            status.value = Status.Error.IOFailure(e)
                            running = null
                        }
                    }
                    return@runOnDefaultDispatcher
                }

                if (isCanceled()) {
                    return@runOnDefaultDispatcher
                }

                val code = response.code
                val elapsed = SystemClock.elapsedRealtime() - start
                response.closeQuietly()
                runOnMainDispatcher {
                    status.value =
                        if (code == 204 || code == 200) {
                            Status.Success(elapsed)
                        } else {
                            Status.Error.UnexpectedResponseCode(code)
                        }
                    running = null
                }
            }
        }

    }

    private fun cancelTest() {
        running?.cancel()
        running = null
    }

    fun invalidate() {
        cancelTest()
        status.value = Status.Idle
    }

}

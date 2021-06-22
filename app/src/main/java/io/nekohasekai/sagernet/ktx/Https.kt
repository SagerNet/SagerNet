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

package io.nekohasekai.sagernet.ktx

import android.os.Build
import androidx.fragment.app.Fragment
import cn.hutool.core.lang.Validator
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

val okHttpClient = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

private lateinit var proxyClient: OkHttpClient
fun Fragment.createHttpClient(): OkHttpClient {
    if ((activity as MainActivity?)?.state != BaseService.State.Connected) {
        return okHttpClient
    }

    if (!::proxyClient.isInitialized) {
        proxyClient = okHttpClient.newBuilder().proxy(requireProxy()).build()
    }
    return proxyClient
}

fun requireProxy(): Proxy {
    return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
        Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", DataStore.socksPort))
    } else {
        Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", DataStore.httpPort))
    }
}

fun linkBuilder() = HttpUrl.Builder().scheme("https")

fun HttpUrl.Builder.toLink(scheme: String, appendDefaultPort: Boolean = true): String {
    var url = build()
    val defaultPort = HttpUrl.defaultPort(url.scheme)
    var replace = false
    if (appendDefaultPort && url.port == defaultPort) {
        url = url.newBuilder().port(14514).build()
        replace = true
    }
    return url.toString().replace("${url.scheme}://", "$scheme://").let {
        if (replace) it.replace(":14514", ":$defaultPort") else it
    }
}

fun String.isIpAddress(): Boolean {
    return Validator.isIpv4(this) || Validator.isIpv6(this)
}

fun String.unwrapHost(): String {
    if (startsWith("[") && endsWith("]")) {
        return substring(1, length - 1).unwrapHost()
    }
    return this
}
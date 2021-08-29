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

package io.nekohasekai.sagernet.fmt.http

import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseHttp(link: String): HttpBean {
    val httpUrl = link.toHttpUrlOrNull() ?: error("Invalid http(s) link: $link")

    if (httpUrl.encodedPath != "/") error("Not http proxy")

    return HttpBean().apply {
        serverAddress = httpUrl.host
        serverPort = httpUrl.port
        username = httpUrl.username
        password = httpUrl.password
        sni = httpUrl.queryParameter("sni")
        name = httpUrl.fragment
        tls = httpUrl.scheme == "https"
    }
}

fun HttpBean.toUri(): String {
    val builder = HttpUrl.Builder()
        .scheme(if (tls) "https" else "http")
        .host(serverAddress)
        .port(serverPort)

    if (username.isNotBlank()) {
        builder.username(username)
    }
    if (password.isNotBlank()) {
        builder.password(password)
    }
    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toString()
}
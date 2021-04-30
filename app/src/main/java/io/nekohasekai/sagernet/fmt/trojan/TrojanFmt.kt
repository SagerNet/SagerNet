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

package io.nekohasekai.sagernet.fmt.trojan

import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseTrojan(server: String): TrojanBean {

    val link = server.replace("trojan://", "https://").toHttpUrlOrNull()
        ?: error("invalid trojan link $server")

    return TrojanBean().apply {
        serverAddress = link.host
        serverPort = link.port
        password = link.username

        if (link.password.isNotBlank()) {
            // https://github.com/trojan-gfw/igniter/issues/318
            password += ":" + link.password
        }

        security = link.queryParameter("security") ?: "tls"
        sni = link.queryParameter("sni") ?: ""
        alpn = link.queryParameter("alpn") ?: ""
        flow = link.queryParameter("flow") ?: ""
        name = link.fragment ?: ""
    }

}

fun TrojanBean.toUri(): String {

    val builder = HttpUrl.Builder()
        .scheme("https")
        .username(password.urlSafe())
        .host(serverAddress)
        .port(serverPort)

    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (alpn.isNotBlank()) {
        builder.addQueryParameter("alpn", alpn)
    }

    when (security) {
        "tls" -> {}
        "xtls" -> {
            builder.addQueryParameter("security", security)
            builder.addQueryParameter("flow", flow)
        }
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }


    return builder.toString().replace("https://", "trojan://")

}
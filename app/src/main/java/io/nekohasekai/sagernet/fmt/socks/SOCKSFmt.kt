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

package io.nekohasekai.sagernet.fmt.socks

import cn.hutool.core.codec.Base64
import io.nekohasekai.sagernet.ktx.queryParameter
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore

fun parseSOCKS(link: String): SOCKSBean {
    if (!link.substringAfter("://").contains(":")) {
        // v2rayN shit format
        val url = Libcore.parseURL(link)
        return SOCKSBean().apply {
            serverAddress = url.host
            serverPort = url.port
            username = url.username.takeIf { it != "null" } ?: ""
            password = url.password.takeIf { it != "null" } ?: ""
            if (link.contains("#")) {
                name = link.substringAfter("#").unUrlSafe()
            }
        }
    } else {
        val url = Libcore.parseURL(link)

        return SOCKSBean().apply {
            protocol = when {
                link.startsWith("socks4://") -> SOCKSBean.PROTOCOL_SOCKS4
                link.startsWith("socks4a://") -> SOCKSBean.PROTOCOL_SOCKS4A
                else -> SOCKSBean.PROTOCOL_SOCKS5
            }
            serverAddress = url.host
            serverPort = url.port
            username = url.username
            password = url.password
            name = url.fragment
            tls = url.queryParameter("tls") == "true"
            sni = url.queryParameter("sni")
        }
    }
}

fun SOCKSBean.toUri(): String {
    val builder = Libcore.newURL("socks${protocolVersion()}")
    builder.host = serverAddress
    builder.port = serverPort
    if (!username.isNullOrBlank()) builder.username = username
    if (!password.isNullOrBlank()) builder.password = password
    if (tls) {
        builder.addQueryParameter("tls", "true")
        if (sni.isNotBlank()) {
            builder.addQueryParameter("sni", sni)
        }
    }
    if (!name.isNullOrBlank()) builder.setRawFragment(name.urlSafe())
    return builder.string

}

fun SOCKSBean.toV2rayN(): String {

    var link = ""
    if (username.isNotBlank()) {
        link += username.urlSafe() + ":" + password.urlSafe() + "@"
    }
    link += "$serverAddress:$serverPort"
    link = "socks://" + Base64.encode(link)
    if (name.isNotBlank()) {
        link += "#" + name.urlSafe()
    }

    return link

}
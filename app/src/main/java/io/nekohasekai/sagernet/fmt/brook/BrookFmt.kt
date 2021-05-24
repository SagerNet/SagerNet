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

package io.nekohasekai.sagernet.fmt.brook

import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseBrook(text: String): AbstractBean {
    if (!text.contains(":") && !text.contains("@")) {

        // https://txthinking.github.io/brook/#/brook-link
        var server = text.substringAfter("brook://").unUrlSafe()
        if (server.startsWith("socks5://")) {
            server = server.substringAfter("://")
            val bean = SOCKSBean()
            bean.serverAddress = server.substringBefore(":")
            bean.serverPort = server.substringAfter(":").substringBefore(" ").toInt()
            server = server.substringAfter(":").substringAfter(" ")
            if (server.contains(" ")) {
                bean.username = server.substringBefore(" ")
                bean.password = server.substringAfter(" ")
            }
            return bean.applyDefaultValues()
        }

        val bean = BrookBean()

        when {
            server.startsWith("ws://") -> {
                bean.protocol = "ws"
                server = server.substringAfter("://")
            }
            server.startsWith("wss://") -> {
                bean.protocol = "wss"
                server = server.substringAfter("://")
            }
            else -> {
                bean.protocol = ""
            }
        }

        if (server.contains(" ")) {
            bean.password = server.substringAfter(" ")
            server = server.substringBefore(" ")
        }

        val url =
            "https://$server".toHttpUrlOrNull() ?: error("Invalid brook link: $text ($server)")

        bean.serverAddress = url.host
        bean.serverPort = url.port
      //  bean.name = url.fragment
        if (server.contains("/")) {
            bean.wsPath = url.encodedPath.unUrlSafe()
        }

        return bean.applyDefaultValues()
    } else {
        /**
         * brook://urlEncode(password)@host:port#urlEncode(remarks)
         * brook+ws(s)://urlEncode(password)@host:port?path=...#urlEncode(remarks)
         */
        val proto = if (!text.startsWith("brook+")) "" else {
            text.substringAfter("+").substringBefore("://")
        }

        if (proto !in arrayOf("", "ws", "wss")) error("Invalid brook protocol $proto")

        val link = ("https://" + text.substringAfter("://")).toHttpUrlOrNull()
            ?: error("Invalid brook url: $text")

        return BrookBean().apply {
            protocol = proto
            serverAddress = link.host
            serverPort = link.port
            link.queryParameter("path")?.also {
                wsPath = it
            }
            name = link.fragment
        }
    }
}

fun BrookBean.toUri(): String {
    /*var server = when (protocol) {
        "ws" -> "ws://$serverAddress:$serverPort"
        "wss" -> "wss://$serverAddress:$serverPort"
        else -> "$serverAddress:$serverPort"
    }
    if (protocol.startsWith("ws")) {
        if (wsPath.isNotBlank()) {
            if (!wsPath.startsWith("/")) {
                server += "/"
            }
            server += wsPath.pathSafe()
        }
    }
    //if (name.isNotBlank()) {
    //    server += "#" + name.urlSafe()
    //}
    if (password.isNotBlank()) {
        server = "$server $password"
    }
    return "brook://" + server.urlSafe()*/
    val builder = linkBuilder()
        .host(serverAddress)
        .port(serverPort)

    if (password.isNotBlank()) {
        builder.encodedUsername(password.urlSafe())
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    if (wsPath.isNotBlank()) {
        builder.addQueryParameter("path", wsPath)
    }

    return when (protocol) {
        "ws", "wss" -> builder.toLink("brook+$protocol", false)
        else -> builder.toLink("brook")
    }

}

fun BrookBean.internalUri(): String {
    var server = "${serverAddress}:${serverPort}"
    server = when (protocol) {
        "ws" -> "ws://"
        "wss" -> "wss://"
        else -> return server
    } + server
    if (wsPath.isNotBlank()) {
        if (!wsPath.startsWith("/")) {
            server += "/"
        }
        server += wsPath.pathSafe()
    }
    return server
}
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

package io.nekohasekai.sagernet.fmt.brook

import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

val kinds = arrayOf("server", "wsserver", "wssserver", "socks5")

fun parseBrook(text: String): AbstractBean {
    if (!(text.contains("([?@])".toRegex()))) {

        // https://txthinking.github.io/brook/#/brook-link
        // old brook scheme
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
    } else if (text.matches("^brook://(${kinds.joinToString("|")})\\?.+".toRegex())) {

        // https://github.com/txthinking/brook/issues/811

        val link = ("https://" + text.substringAfter("://")).toHttpUrlOrNull()
            ?: error("Invalid brook url: $text")

        val bean = if (link.host == "socks5") SOCKSBean() else BrookBean()
        bean.name = link.queryParameter("remarks")

        when (link.host) {
            "server" -> {
                bean as BrookBean
                bean.protocol = ""

                val server = link.queryParameter("server")
                    ?: error("Invalid brook server url (Missing server parameter): $text")

                bean.serverAddress = server.substringBefore(":")
                bean.serverPort = server.substringAfter(":").toInt()
                bean.password = link.queryParameter("password")
                    ?: error("Invalid brook server url (Missing password parameter): $text")
            }
            "wsserver" -> {
                bean as BrookBean
                bean.protocol = "ws"


                var wsserver = (link.queryParameter("wsserver")
                    ?: error("Invalid brook wsserver url (Missing wsserver parameter): $text"))
                    .substringAfter("://")
                if (wsserver.contains("/")) {
                    bean.wsPath = "/" + wsserver.substringAfter("/")
                    wsserver = wsserver.substringBefore("/")
                }
                bean.serverAddress = wsserver.substringBefore(":")
                bean.serverPort = wsserver.substringAfter(":").toInt()
                bean.password = link.queryParameter("password")
                    ?: error("Invalid brook wsserver url (Missing password parameter): $text")

            }
            "wssserver" -> {
                bean as BrookBean
                bean.protocol = "wss"


                var wsserver = (link.queryParameter("wssserver")
                    ?: error("Invalid brook wssserver url (Missing wssserver parameter): $text"))
                    .substringAfter("://")
                if (wsserver.contains("/")) {
                    bean.wsPath = "/" + wsserver.substringAfter("/")
                    wsserver = wsserver.substringBefore("/")
                }
                bean.serverAddress = wsserver.substringBefore(":")
                bean.serverPort = wsserver.substringAfter(":").toInt()
                bean.password = link.queryParameter("password")
                    ?: error("Invalid brook wssserver url (Missing password parameter): $text")

            }
            "socks5" -> {
                bean as SOCKSBean

                val socks5 = (link.queryParameter("socks5")
                    ?: error("Invalid brook socks5 url (Missing socks5 parameter): $text"))
                    .substringAfter("://")

                bean.serverAddress = socks5.substringBefore(":")
                bean.serverPort = socks5.substringAfter(":").toInt()

                link.queryParameter("username")?.also { username ->
                    bean.username = username

                    link.queryParameter("password")?.also { password ->
                        bean.password = password
                    }
                }
            }
        }

        return bean

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
            password = link.username
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
    var server = wrapUri()
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
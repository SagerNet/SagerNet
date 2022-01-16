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
import libcore.Libcore

fun parseBrook(text: String): AbstractBean {
    // https://github.com/txthinking/brook/issues/811

    val link = Libcore.parseURL(text)

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
                ?: error("Invalid brook wsserver url (Missing wsserver parameter): $text")).substringAfter(
                "://"
            )
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
                ?: error("Invalid brook wssserver url (Missing wssserver parameter): $text")).substringAfter(
                "://"
            )
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
                ?: error("Invalid brook socks5 url (Missing socks5 parameter): $text")).substringAfter(
                "://"
            )

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
}

fun BrookBean.toUri(): String {
    val builder = Libcore.newURL("brook")
    var serverString = "$serverAddress:$serverPort"
    if (protocol.startsWith("ws")) {
        if (wsPath.isNotBlank() && wsPath != "/") {
            if (!wsPath.startsWith("/")) wsPath = "/$wsPath"
            serverString += wsPath
        }
    }
    when (protocol) {
        "ws" -> {
            builder.host = "wsserver"
            builder.addQueryParameter("wsserver", serverString)
        }
        "wss" -> {
            builder.host = "wssserver"
            builder.addQueryParameter("wssserver", serverString)
        }
        else -> {
            builder.host = "server"
            builder.addQueryParameter("server", serverString)
        }
    }
    if (password.isNotBlank()) {
        builder.addQueryParameter("password", password)
    }
    if (name.isNotBlank()) {
        builder.addQueryParameter("remarks", name)
    }
    return builder.string
}

fun BrookBean.internalUri(): String {
    var server = when (protocol) {
        "ws" -> "ws://" + wrapUriWithOriginHost()
        "wss" -> "wss://" + wrapUriWithOriginHost()
        else -> return wrapUri()
    }
    if (wsPath.isNotBlank()) {
        if (!wsPath.startsWith("/")) {
            server += "/"
        }
        server += wsPath.pathSafe()
    }
    return server
}
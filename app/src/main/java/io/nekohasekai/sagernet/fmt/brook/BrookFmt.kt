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
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe

// https://txthinking.github.io/brook/#/brook-link
fun parseBrook(text: String): AbstractBean {
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

    if (server.startsWith("ws://")) {
        bean.protocol = "ws"
        server = server.substringAfter("://")
    } else if (server.startsWith("wss://")) {
        bean.protocol = "wss"
        server = server.substringAfter("://")
    } else {
        bean.protocol = ""
    }

    bean.serverAddress = server.substringBefore(":")
    bean.serverPort = server.substringAfter(":").substringBefore(" ").toInt()
    server = server.substringAfter(":")
    if (server.contains(" ")) {
        bean.password = server.substringAfter(" ")
    }
    return bean.applyDefaultValues()
}

fun BrookBean.toUri(): String {
    var server = when (protocol) {
        "ws" -> "ws://$serverAddress:$serverPort"
        "wss" -> "wss://$serverAddress:$serverPort"
        else -> "$serverAddress:$serverPort"
    }
    if (password.isNotBlank()) {
        server = "$server $password"
    }
    return "brook://" + server.urlSafe()
}
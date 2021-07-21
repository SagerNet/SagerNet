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

package io.nekohasekai.sagernet.fmt.relaybaton

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseRelayBaton(link: String): RelayBatonBean {
    val url = (link.replace("relaybaton://", "https://")).toHttpUrlOrNull()
        ?: error("Invalid relaybaton link: $link")
    return RelayBatonBean().apply {
        serverAddress = url.host
        username = url.username
        password = url.password
        name = url.fragment
        initializeDefaultValues()
    }
}

fun RelayBatonBean.toUri(): String {
    val builder = linkBuilder().host(serverAddress).username(username).password(password)

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("relaybaton", false)
}

fun RelayBatonBean.buildRelayBatonConfig(port: Int): String {
    return """
        [client]
        port = $port
        http_port = 0
        redir_port = 0
        server = "$finalAddress"
        username = "$username"
        password = "$password"
        proxy_all = true

        [dns]
        type = "default"
       
        [log]
        file = "stdout"
        level = "${if (DataStore.enableLog) "trace" else "error"}"
    """.trimIndent()
}
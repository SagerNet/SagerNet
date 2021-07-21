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

package io.nekohasekai.sagernet.fmt.pingtunnel

import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull


/**
 * Unofficial
 *
 * ping-tunnel://[urlEncode(key)@]host[#urlEncode(remarks)]
 */

fun parsePingTunnel(server: String): PingTunnelBean {
    val link = server.replace("ping-tunnel://", "https://").toHttpUrlOrNull()
        ?: error("invalid PingTunnel link $server")
    return PingTunnelBean().apply {
        serverAddress = link.host
        key = link.username
        link.fragment.takeIf { !it.isNullOrBlank() }?.let {
            name = it
        }
        initializeDefaultValues()
    }
}

fun PingTunnelBean.toUri(): String {
    val builder = linkBuilder().host(serverAddress)
    if (key.isNotBlank() && key != "1") {
        builder.encodedUsername(key.urlSafe())
    }
    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }
    return builder.toLink("ping-tunnel", false)
}
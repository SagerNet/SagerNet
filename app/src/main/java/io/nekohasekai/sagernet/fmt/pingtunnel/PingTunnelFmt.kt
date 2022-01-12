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

package io.nekohasekai.sagernet.fmt.pingtunnel

import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore


/**
 * Unofficial
 *
 * ping-tunnel://[urlEncode(key)@]host[#urlEncode(remarks)]
 */

fun parsePingTunnel(server: String): PingTunnelBean {
    val link = Libcore.parseURL(server)
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
    val builder = Libcore.newURL("ping-tunnel")
    builder.host = serverAddress
    if (key.isNotBlank() && key != "1") {
        builder.username = key
    }
    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }
    return builder.string
}
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

package io.nekohasekai.sagernet.fmt.relaybaton

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore

fun parseRelayBaton(link: String): RelayBatonBean {
    val url = Libcore.parseURL(link)
    return RelayBatonBean().apply {
        serverAddress = url.host
        username = url.username
        password = url.password
        name = url.fragment
        initializeDefaultValues()
    }
}

fun RelayBatonBean.toUri(): String {
    val builder = Libcore.newURL("relaybaton")
    builder.host = serverAddress
    builder.username = username
    builder.password = password

    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }

    return builder.string
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
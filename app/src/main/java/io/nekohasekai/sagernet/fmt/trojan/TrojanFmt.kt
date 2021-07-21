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

import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// WTF
// https://github.com/trojan-gfw/igniter/issues/318
fun parseTrojan(server: String): TrojanBean {

    val link = server.replace("trojan://", "https://").toHttpUrlOrNull()
        ?: error("invalid trojan link $server")

    return TrojanBean().apply {
        serverAddress = link.host
        serverPort = link.port
        password = link.username

        if (link.password.isNotBlank()) {
            password += ":" + link.password
        }

        security = link.queryParameter("security") ?: "tls"
        sni = link.queryParameter("sni") ?: ""
        alpn = link.queryParameter("alpn") ?: ""
        name = link.fragment ?: ""
    }

}

fun TrojanBean.toUri(): String {

    val builder = linkBuilder().username(password).host(serverAddress).port(serverPort)

    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (alpn.isNotBlank()) {
        builder.addQueryParameter("alpn", alpn)
    }

    when (security) {
        "tls" -> {
        }
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }


    return builder.toLink("trojan")

}

fun TrojanBean.buildTrojanConfig(port: Int): String {
    return JSONObject().also { conf ->
        conf["run_type"] = "client"
        conf["local_addr"] = LOCALHOST
        conf["local_port"] = port
        conf["remote_addr"] = finalAddress
        conf["remote_port"] = finalPort
        conf["password"] = JSONArray().apply {
            add(password)
        }
        conf["log_level"] = if (DataStore.enableLog) 0 else 2

        conf["ssl"] = JSONObject().also {
            if (allowInsecure) it["verify"] = false
            if (sni.isBlank() && finalAddress == LOCALHOST && !serverAddress.isIpAddress()) {
                sni = serverAddress
            }
            if (sni.isNotBlank()) it["sni"] = sni
            if (alpn.isNotBlank()) it["alpn"] = JSONArray(alpn.split("\n"))
        }
    }.toStringPretty()
}

fun TrojanBean.buildTrojanGoConfig(port: Int, mux: Boolean): String {
    return JSONObject().also { conf ->
        conf["run_type"] = "client"
        conf["local_addr"] = LOCALHOST
        conf["local_port"] = port
        conf["remote_addr"] = finalAddress
        conf["remote_port"] = finalPort
        conf["password"] = JSONArray().apply {
            add(password)
        }
        conf["log_level"] = if (DataStore.enableLog) 0 else 2
        if (mux && DataStore.enableMuxForAll) conf["mux"] = JSONObject().also {
            it["enabled"] = true
            it["concurrency"] = DataStore.muxConcurrency
        }
        conf["tcp"] = JSONObject().also {
            it["prefer_ipv4"] = DataStore.ipv6Mode <= IPv6Mode.ENABLE
        }

        conf["ssl"] = JSONObject().also {
            if (allowInsecure) it["verify"] = false
            if (sni.isBlank() && finalAddress == LOCALHOST && !serverAddress.isIpAddress()) {
                sni = serverAddress
            }
            if (sni.isNotBlank()) it["sni"] = sni
            if (alpn.isNotBlank()) it["alpn"] = JSONArray(alpn.split("\n"))
        }
    }.toStringPretty()
}
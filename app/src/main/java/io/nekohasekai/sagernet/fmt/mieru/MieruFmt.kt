/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.fmt.mieru

import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.mkPort

fun MieruBean.buildMieruConfig(port: Int): String {
    return JSONObject().also {
        it["activeProfile"] = "default"
        it["socks5Port"] = port
        it["loggingLevel"] = if (DataStore.enableLog) "DEBUG" else "WARN"
        it["profiles"] = JSONArray().apply {
            put(JSONObject().also {
                it["profileName"] = "default"
                it["user"] = JSONObject().also {
                    it["name"] = username
                    it["password"] = password
                }
                it["servers"] = JSONArray().apply {
                    put(JSONObject().also {
                        it["ipAddress"] = finalAddress
                        it["portBindings"] = JSONArray().apply {
                            put(JSONObject().also {
                                it["port"] = finalPort
                                it["protocol"] = when (protocol) {
                                    MieruBean.PROTOCOL_TCP -> "TCP"
                                    MieruBean.PROTOCOL_UDP -> "UDP"
                                    else -> error("unexpected protocol $protocol")
                                }
                            })
                        }
                    })
                }
            })
        }
    }.toStringPretty()
}

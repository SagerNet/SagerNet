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

package io.nekohasekai.sagernet.ktx

import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.http.parseHttp
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.shadowsocksr.parseShadowsocksR
import io.nekohasekai.sagernet.fmt.socks.parseSOCKS
import io.nekohasekai.sagernet.fmt.trojan.parseTrojan
import io.nekohasekai.sagernet.fmt.v2ray.parseV2Ray
import java.util.*

fun formatObject(obj: Any): String {
    return gson.toJson(obj).let { JSONObject(it).toStringPretty() }
}

fun parseProxies(text: String): List<AbstractBean> {
    val links = text.split('\n').flatMap { it.trim().split(' ') }
    val entities = LinkedList<AbstractBean>()
    for (link in links) {
        if (link.startsWith("socks://")) {
            Logs.d("Try parse socks link: $link")
            runCatching {
                entities.add(parseSOCKS(link))
            }.onFailure {
                Logs.w(it)
            }
        } else if (link.matches("(http|https|naive\\+https)://.*".toRegex())) {
            Logs.d("Try parse http link: $link")
            runCatching {
                entities.add(parseHttp(link))
            }.onFailure {
                Logs.w(it)
            }
        } else if (link.startsWith("vmess://") || link.startsWith("vless://")) {
            Logs.d("Try parse v2ray link: $link")
            runCatching {
                entities.add(parseV2Ray(link))
            }.onFailure {
                Logs.w(it)
            }
        } else if (link.startsWith("trojan://")) {
            Logs.d("Try parse trojan link: $link")
            runCatching {
                entities.add(parseTrojan(link))
            }.onFailure {
                Logs.w(it)
            }
        } else if (link.startsWith("ss://")) {
            Logs.d("Try parse shadowsocks link: $link")
            runCatching {
                entities.add(parseShadowsocks(link))
            }.onFailure {
                Logs.w(it)
            }
        } else if (link.startsWith("ssr://")) {
            Logs.d("Try parse shadowsocksr link: $link")
            runCatching {
                entities.add(parseShadowsocksR(link))
            }.onFailure {
                Logs.w(it)
            }
        }
    }
    entities.forEach { it.initDefaultValues() }
    return entities
}
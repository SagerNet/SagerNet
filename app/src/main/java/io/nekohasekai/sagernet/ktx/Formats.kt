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

package io.nekohasekai.sagernet.ktx

import cn.hutool.core.codec.Base64
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.Serializable
import io.nekohasekai.sagernet.fmt.brook.parseBrook
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.http.parseHttp
import io.nekohasekai.sagernet.fmt.naive.parseNaive
import io.nekohasekai.sagernet.fmt.parseUniversal
import io.nekohasekai.sagernet.fmt.pingtunnel.parsePingTunnel
import io.nekohasekai.sagernet.fmt.relaybaton.parseRelayBaton
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.shadowsocksr.parseShadowsocksR
import io.nekohasekai.sagernet.fmt.socks.parseSOCKS
import io.nekohasekai.sagernet.fmt.trojan.parseTrojan
import io.nekohasekai.sagernet.fmt.trojan_go.parseTrojanGo
import io.nekohasekai.sagernet.fmt.v2ray.parseV2Ray

fun formatObject(obj: Any): String {
    return gson.toJson(obj).let { JSONObject(it).toStringPretty() }
}

fun String.decodeBase64UrlSafe(): String {
    return Base64.decodeStr(
        replace(' ', '-').replace('/', '_').replace('+', '-').replace("=", "")
    )
}

class SubscriptionFoundException(val link: String) : RuntimeException()

fun parseProxies(text: String): List<AbstractBean> {
    val links = text.split('\n').flatMap { it.trim().split(' ') }
    val linksByLine = text.split('\n').map { it.trim() }

    val entities = ArrayList<AbstractBean>()
    val entitiesByLine = ArrayList<AbstractBean>()

    fun String.parseLink(entities: ArrayList<AbstractBean>) {
        if (startsWith("clash://install-config?") || startsWith("sn://subscription?")) {
            throw SubscriptionFoundException(this)
        }

        if (startsWith("sn://")) {
            Logs.d("Try parse universal link: $this")
            runCatching {
                entities.add(parseUniversal(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("socks://") || startsWith("socks4://") || startsWith("socks4a://") || startsWith("socks5://")) {
            Logs.d("Try parse socks link: $this")
            runCatching {
                entities.add(parseSOCKS(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (matches("(http|https)://.*".toRegex())) {
            Logs.d("Try parse http link: $this")
            runCatching {
                entities.add(parseHttp(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("vmess://") || startsWith("vless://")) {
            Logs.d("Try parse v2ray link: $this")
            runCatching {
                entities.add(parseV2Ray(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("trojan://")) {
            Logs.d("Try parse trojan link: $this")
            runCatching {
                entities.add(parseTrojan(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("trojan-go://")) {
            Logs.d("Try parse trojan-go link: $this")
            runCatching {
                entities.add(parseTrojanGo(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("ss://")) {
            Logs.d("Try parse shadowsocks link: $this")
            runCatching {
                entities.add(parseShadowsocks(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("ssr://")) {
            Logs.d("Try parse shadowsocksr link: $this")
            runCatching {
                entities.add(parseShadowsocksR(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("naive+")) {
            Logs.d("Try parse naive link: $this")
            runCatching {
                entities.add(parseNaive(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("ping-tunnel://")) {
            Logs.d("Try parse pt link: $this")
            runCatching {
                entities.add(parsePingTunnel(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("relaybaton://")) {
            Logs.d("Try parse rb link: $this")
            runCatching {
                entities.add(parseRelayBaton(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("brook://")) {
            Logs.d("Try parse brook link: $this")
            runCatching {
                entities.add(parseBrook(this))
            }.onFailure {
                Logs.w(it)
            }
        }
    }

    for (link in links) {
        link.parseLink(entities)
    }
    for (link in linksByLine) {
        link.parseLink(entitiesByLine)
    }
    var isBadLink = false
    if (entities.onEach { it.initializeDefaultValues() }.size == entitiesByLine.onEach { it.initializeDefaultValues() }.size) run test@{
        entities.forEachIndexed { index, bean ->
            val lineBean = entitiesByLine[index]
            if (bean == lineBean && bean.displayName() != lineBean.displayName()) {
                isBadLink = true
                return@test
            }
        }
    }
    return if (entities.size > entitiesByLine.size) entities else entitiesByLine
}

fun <T : Serializable> T.applyDefaultValues(): T {
    initializeDefaultValues()
    return this
}
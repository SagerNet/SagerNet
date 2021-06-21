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

package io.nekohasekai.sagernet.fmt.shadowsocksr

import cn.hutool.core.codec.Base64
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.*

fun parseShadowsocksR(url: String): ShadowsocksRBean {

    val params = url.substringAfter("ssr://").decodeBase64UrlSafe().split(":")

    val bean = ShadowsocksRBean().apply {
        serverAddress = params[0]
        serverPort = params[1].toInt()
        protocol = params[2]
        method = params[3]
        obfs = params[4]
        password = params[5].substringBefore("/").decodeBase64UrlSafe()
    }

    val httpUrl = ("https://localhost" + params[5].substringAfter("/")).toHttpUrl()

    runCatching {
        bean.obfsParam = httpUrl.queryParameter("obfsparam")!!.decodeBase64UrlSafe()
    }
    runCatching {
        bean.protocolParam = httpUrl.queryParameter("protoparam")!!.decodeBase64UrlSafe()
    }

    val remarks = httpUrl.queryParameter("remarks")
    if (!remarks.isNullOrBlank()) {
        bean.name = remarks.decodeBase64UrlSafe()
    }

    return bean

}

fun ShadowsocksRBean.toUri(): String {

    return "ssr://" + Base64.encodeUrlSafe(
        "%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s&remarks=%s".format(
            Locale.ENGLISH, serverAddress, serverPort, protocol, method, obfs,
            Base64.encodeUrlSafe("%s".format(Locale.ENGLISH, password)),
            Base64.encodeUrlSafe("%s".format(Locale.ENGLISH, obfsParam)),
            Base64.encodeUrlSafe("%s".format(Locale.ENGLISH, protocolParam)),
            Base64.encodeUrlSafe("%s".format(Locale.ENGLISH, name ?: ""))
        )
    )
}

fun ShadowsocksRBean.buildShadowsocksRConfig(): String {
    return JSONObject().also {
        it["server"] = serverAddress
        it["server_port"] = serverPort
        it["method"] = method
        it["password"] = password
        it["protocol"] = protocol
        it["protocol_param"] = protocolParam
        it["obfs"] = obfs
        it["obfs_param"] = obfsParam
        it["ipv6"] =  DataStore.ipv6Mode >= IPv6Mode.ENABLE
    }.toStringPretty()
}

fun JSONObject.parseShadowsocksR(): ShadowsocksRBean {
    return ShadowsocksRBean().applyDefaultValues().apply {
        serverAddress = getStr("server", serverAddress)
        serverPort = getInt("server_port", serverPort)
        method = getStr("method", method)
        password = getStr("password", password)
        protocol = getStr("protocol", protocol)
        protocolParam = getStr("protocol_param", protocolParam)
        obfs = getStr("obfs", obfs)
        obfsParam = getStr("obfs_param", obfsParam)
        name = getStr("remarks", name)
    }
}
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.*

fun parseShadowsocksR(url: String): ShadowsocksRBean {

    val params = Base64.decodeStr(url.substringAfter("ssr://")).split(":")

    val bean = ShadowsocksRBean().apply {
        serverAddress = params[0]
        serverPort = params[1].toInt()
        protocol = params[2]
        method = params[3]
        obfs = params[4]
        password = Base64.decodeStr(params[5].substringBefore("/"))
    }

    val httpUrl = ("https://localhost" + params[5].substringAfter("/")).toHttpUrl()

    runCatching {
        bean.obfsParam = Base64.decodeStr(httpUrl.queryParameter("obfsparam")!!)
    }
    runCatching {
        bean.protocolParam = Base64.decodeStr(httpUrl.queryParameter("protoparam")!!)
    }

    val remarks = httpUrl.queryParameter("remarks")
    if (!remarks.isNullOrBlank()) {
        bean.name = Base64.decodeStr(remarks)
    }

    return bean

}

fun ShadowsocksRBean.toUri(): String {

    return "ssr://" + Base64.encodeUrlSafe("%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s&remarks=%s".format(
        Locale.ENGLISH, serverAddress, serverPort, protocol, method, obfs,
        Base64.encodeUrlSafe("%s".format(Locale.ENGLISH, password)),
        Base64.encodeUrlSafe("%s".format(Locale.ENGLISH, obfsParam)),
        Base64.encodeUrlSafe("%s".format(Locale.ENGLISH, protocolParam)),
        Base64.encodeUrlSafe("%s".format(Locale.ENGLISH, name ?: ""))))
}
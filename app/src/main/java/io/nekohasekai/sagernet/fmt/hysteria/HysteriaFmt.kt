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

package io.nekohasekai.sagernet.fmt.hysteria

import cn.hutool.core.util.NumberUtil
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore
import java.io.File


// hysteria://host:port?auth=123456&peer=sni.domain&insecure=1|0&upmbps=100&downmbps=100&alpn=hysteria&obfs=xplus&obfsParam=123456#remarks

fun parseHysteria(url: String): HysteriaBean {
    val link = Libcore.parseURL(url)

    return HysteriaBean().apply {
        serverAddress = link.host
        serverPort = link.port
        name = link.fragment

        link.queryParameter("peer")?.also {
            sni = it
        }
        link.queryParameter("auth")?.takeIf { it.isNotBlank() }?.also {
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = it
        }
        link.queryParameter("insecure")?.also {
            allowInsecure = it == "1"
        }
        link.queryParameter("upmbps")?.also {
            uploadMbps = it.toIntOrNull() ?: uploadMbps
        }
        link.queryParameter("downmbps")?.also {
            downloadMbps = it.toIntOrNull() ?: downloadMbps
        }
        link.queryParameter("alpn")?.also {
            alpn = it
        }
        link.queryParameter("obfsParam")?.also {
            obfuscation = it
        }
        link.queryParameter("protocol")?.also {
            when (it) {
                "faketcp" -> {
                    protocol = HysteriaBean.PROTOCOL_FAKETCP
                }
                "wechat-video" -> {
                    protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO
                }
            }
        }
    }
}

fun HysteriaBean.toUri(): String {
    val builder = Libcore.newURL("hysteria")
    builder.host = serverAddress
    builder.port = serverPort

    if (sni.isNotBlank()) {
        builder.addQueryParameter("peer", sni)
    }
    if (authPayload.isNotBlank()) {
        builder.addQueryParameter("auth", authPayload)
    }
    if (uploadMbps != 10) {
        builder.addQueryParameter("upmbps", "$uploadMbps")
    }
    if (downloadMbps != 50) {
        builder.addQueryParameter("downmbps", "$downloadMbps")
    }
    if (alpn.isNotBlank()) {
        builder.addQueryParameter("alpn", alpn)
    }
    if (obfuscation.isNotBlank()) {
        builder.addQueryParameter("obfs", "xplus")
        builder.addQueryParameter("obfsParam", obfuscation)
    }
    when (protocol) {
        HysteriaBean.PROTOCOL_FAKETCP -> {
            builder.addQueryParameter("protocol", "faketcp")
        }
        HysteriaBean.PROTOCOL_WECHAT_VIDEO -> {
            builder.addQueryParameter("protocol", "wechat-video")
        }
    }
    if (protocol == HysteriaBean.PROTOCOL_FAKETCP) {
        builder.addQueryParameter("protocol", "faketcp")
    }
    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }
    return builder.string
}

fun JSONObject.parseHysteria(): HysteriaBean {
    return HysteriaBean().apply {
        serverAddress = getStr("server").substringBeforeLast(":")
        serverPort = getStr("server").substringAfterLast(":")
            .takeIf { NumberUtil.isInteger(it) }
            ?.toInt() ?: 443
        uploadMbps = getInt("up_mbps")
        downloadMbps = getInt("down_mbps")
        obfuscation = getStr("obfs")
        getStr("auth")?.also {
            authPayloadType = HysteriaBean.TYPE_BASE64
            authPayload = it
        }
        getStr("auth_str")?.also {
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = it
        }
        getStr("protocol")?.also {
            when (it) {
                "faketcp" -> {
                    protocol = HysteriaBean.PROTOCOL_FAKETCP
                }
                "wechat-video" -> {
                    protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO
                }
            }
        }
        sni = getStr("server_name")
        alpn = getStr("alpn")
        allowInsecure = getBool("insecure")

        streamReceiveWindow = getInt("recv_window_conn")
        connectionReceiveWindow = getInt("recv_window")
        disableMtuDiscovery = getBool("disable_mtu_discovery")
    }
}

fun HysteriaBean.buildHysteriaConfig(port: Int, cacheFile: (() -> File)?): String {
    return JSONObject().also {
        it["server"] = wrapUri()
        when (protocol) {
            HysteriaBean.PROTOCOL_FAKETCP -> {
                it["protocol"] = "faketcp"
            }
            HysteriaBean.PROTOCOL_WECHAT_VIDEO -> {
                it["protocol"] = "wechat-video"
            }
        }
        it["up_mbps"] = uploadMbps
        it["down_mbps"] = downloadMbps
        it["socks5"] = JSONObject(mapOf("listen" to "$LOCALHOST:$port"))
        it["obfs"] = obfuscation
        when (authPayloadType) {
            HysteriaBean.TYPE_BASE64 -> it["auth"] = authPayload
            HysteriaBean.TYPE_STRING -> it["auth_str"] = authPayload
        }
        if (sni.isBlank() && finalAddress == LOCALHOST && !serverAddress.isIpAddress()) {
            sni = serverAddress
        }
        if (sni.isNotBlank()) {
            it["server_name"] = sni
        }
        if (alpn.isNotBlank()) it["alpn"] = alpn
        if (caText.isNotBlank() && cacheFile != null) {
            val caFile = cacheFile()
            caFile.writeText(caText)
            it["ca"] = caFile.absolutePath
        }

        if (allowInsecure) it["insecure"] = true
        if (streamReceiveWindow > 0) it["recv_window_conn"] = streamReceiveWindow
        if (connectionReceiveWindow > 0) it["recv_window"] = connectionReceiveWindow
        if (disableMtuDiscovery) it["disable_mtu_discovery"] = true

        it["resolver"] = "udp://127.0.0.1:" + DataStore.localDNSPort
    }.toStringPretty()
}

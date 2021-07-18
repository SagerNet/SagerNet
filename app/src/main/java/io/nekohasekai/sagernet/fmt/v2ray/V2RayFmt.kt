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

package io.nekohasekai.sagernet.fmt.v2ray

import cn.hutool.core.codec.Base64
import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.ktx.*
import okhttp3.HttpUrl.Companion.toHttpUrl

fun parseV2Ray(link: String): StandardV2RayBean {
    if (!link.contains("@")) {
        return parseV2RayN(link)
    }

    val bean = if (!link.startsWith("vless://")) {
        VMessBean()
    } else {
        VLESSBean()
    }
    val url = link.replace("vmess://", "https://").replace("vless://", "https://").toHttpUrl()

    bean.serverAddress = url.host
    bean.serverPort = url.port
    bean.name = url.fragment

    if (url.password.isNotBlank()) { // https://github.com/v2fly/v2fly-github-io/issues/26
        (bean as VMessBean?) ?: error("Invalid vless url: $link")

        var protocol = url.username
        bean.type = protocol
        bean.alterId = url.password.substringAfterLast('-').toInt()
        bean.uuid = url.password.substringBeforeLast('-')

        if (protocol.endsWith("+tls")) {
            bean.security = "tls"
            protocol = protocol.substring(0, protocol.length - 4)

            url.queryParameter("tlsServerName")?.let {
                if (it.isNotBlank()) {
                    bean.sni = it
                }
            }
        }

        when (protocol) {
            "tcp" -> {
                url.queryParameter("type")?.let { type ->
                    if (type == "http") {
                        bean.headerType = "http"
                        url.queryParameter("host")?.let {
                            bean.host = it
                        }
                    }
                }
            }
            "http" -> {
                url.queryParameter("path")?.let {
                    bean.path = it
                }
                url.queryParameter("host")?.let {
                    bean.host = it.split("|").joinToString(",")
                }
            }
            "ws" -> {
                url.queryParameter("path")?.let {
                    bean.path = it
                }
                url.queryParameter("host")?.let {
                    bean.host = it
                }
            }
            "kcp" -> {
                url.queryParameter("type")?.let {
                    bean.headerType = it
                }
                url.queryParameter("seed")?.let {
                    bean.mKcpSeed = it
                }
            }
            "quic" -> {
                url.queryParameter("security")?.let {
                    bean.quicSecurity = it
                }
                url.queryParameter("key")?.let {
                    bean.quicKey = it
                }
                url.queryParameter("type")?.let {
                    bean.headerType = it
                }
            }
        }
    } else { // https://github.com/XTLS/Xray-core/issues/91

        bean.uuid = url.username
        if (url.pathSegments.size > 1 || url.pathSegments[0].isNotBlank()) {
            bean.path = url.pathSegments.joinToString("/")
        }

        val protocol = url.queryParameter("type") ?: "tcp"
        bean.type = protocol

        when (url.queryParameter("security")) {
            "tls" -> {
                bean.security = "tls"
                url.queryParameter("sni")?.let {
                    bean.sni = it
                }
                url.queryParameter("alpn")?.let {
                    bean.alpn = it
                }
                url.queryParameter("cert")?.let {
                    bean.certificates = it
                }
                url.queryParameter("chain")?.let {
                    bean.pinnedPeerCertificateChainSha256 = it
                }
            }
        }
        when (protocol) {
            "tcp" -> {
                url.queryParameter("headerType")?.let { headerType ->
                    if (headerType == "http") {
                        bean.headerType = headerType
                        url.queryParameter("host")?.let {
                            bean.host = it
                        }
                        url.queryParameter("path")?.let {
                            bean.path = it
                        }
                    }
                }
            }
            "kcp" -> {
                url.queryParameter("headerType")?.let {
                    bean.headerType = it
                }
                url.queryParameter("seed")?.let {
                    bean.mKcpSeed = it
                }
            }
            "http" -> {
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let {
                    bean.path = it
                }
            }
            "ws" -> {
                url.queryParameter("host")?.let {
                    bean.host = it
                }
                url.queryParameter("path")?.let {
                    bean.path = it
                }
                url.queryParameter("ed")?.let { ed ->
                    bean.wsMaxEarlyData = ed.toInt()

                    url.queryParameter("eh")?.let {
                        bean.earlyDataHeaderName = it
                    }
                }
            }
            "quic" -> {
                url.queryParameter("headerType")?.let {
                    bean.headerType = it
                }
                url.queryParameter("quicSecurity")?.let { quicSecurity ->
                    bean.quicSecurity = quicSecurity
                    url.queryParameter("key")?.let {
                        bean.quicKey = it
                    }
                }
            }
            "grpc" -> {
                url.queryParameter("serviceName")?.let {
                    bean.grpcServiceName = it
                }
            }
        }
    }

    Logs.d(formatObject(bean))

    return bean
}

fun parseV2RayN(link: String): VMessBean {
    val result = link.substringAfter("vmess://").decodeBase64UrlSafe()
    if (result.contains("= vmess")) {
        return parseCsvVMess(result)
    }
    val bean = VMessBean()
    val json = JSONObject(result)

    bean.serverAddress = json.getStr("add") ?: ""
    bean.serverPort = json.getInt("port") ?: 1080
    bean.encryption = json.getStr("scy") ?: ""
    bean.uuid = json.getStr("id") ?: ""
    bean.alterId = json.getInt("aid") ?: 0
    bean.type = json.getStr("net") ?: ""
    bean.headerType = json.getStr("type") ?: ""
    bean.host = json.getStr("host") ?: ""
    bean.path = json.getStr("path") ?: ""

    when (bean.headerType) {
        "quic" -> {
            bean.quicSecurity = bean.host
            bean.quicKey = bean.path
        }
        "kcp" -> {
            bean.mKcpSeed = bean.path
        }
        "grpc" -> {
            bean.grpcServiceName = bean.path
        }
    }

    bean.name = json.getStr("ps") ?: ""
    bean.sni = json.getStr("sni") ?: bean.host
    bean.security = json.getStr("tls")

    if (json.getInt("v", 2) < 2) {
        when (bean.type) {
            "ws" -> {
                var path = ""
                var host = ""
                val lstParameter = bean.host.split(";")
                if (lstParameter.isNotEmpty()) {
                    path = lstParameter[0].trim()
                }
                if (lstParameter.size > 1) {
                    path = lstParameter[0].trim()
                    host = lstParameter[1].trim()
                }
                bean.path = path
                bean.host = host
            }
            "h2" -> {
                var path = ""
                var host = ""
                val lstParameter = bean.host.split(";")
                if (lstParameter.isNotEmpty()) {
                    path = lstParameter[0].trim()
                }
                if (lstParameter.size > 1) {
                    path = lstParameter[0].trim()
                    host = lstParameter[1].trim()
                }
                bean.path = path
                bean.host = host
            }
        }
    }

    return bean

}

private fun parseCsvVMess(csv: String): VMessBean {

    val args = csv.split(",")

    val bean = VMessBean()

    bean.serverAddress = args[1]
    bean.serverPort = args[2].toInt()
    bean.encryption = args[3]
    bean.uuid = args[4].replace("\"", "")

    args.subList(5, args.size).forEach {

        when {
            it == "over-tls=true" -> bean.security = "tls"
            it.startsWith("tls-host=") -> bean.host = it.substringAfter("=")
            it.startsWith("obfs=") -> bean.type = it.substringAfter("=")
            it.startsWith("obfs-path=") || it.contains("Host:") -> {
                runCatching {
                    bean.path = it.substringAfter("obfs-path=\"").substringBefore("\"obfs")
                }
                runCatching {
                    bean.host = it.substringAfter("Host:").substringBefore("[")
                }

            }

        }

    }

    return bean

}

fun VMessBean.toV2rayN(): String {

    return "vmess://" + JSONObject().also {

        it["v"] = 2
        it["ps"] = name
        it["add"] = serverAddress
        it["port"] = serverPort
        it["id"] = uuid
        it["aid"] = alterId
        it["net"] = type
        it["host"] = host
        it["path"] = path
        it["type"] = headerType

        when (headerType) {
            "quic" -> {
                it["host"] = quicSecurity
                it["path"] = quicKey
            }
            "kcp" -> {
                it["path"] = mKcpSeed
            }
            "grpc" -> {
                it["path"] = grpcServiceName
            }
        }

        it["tls"] = if (security == "tls") "tls" else ""
        it["sni"] = sni
        it["scy"] = encryption

    }.toString().let { Base64.encodeUrlSafe(it) }

}

fun StandardV2RayBean.toUri(standard: Boolean = true): String {
    if (this is VMessBean && alterId > 0) return toV2rayN()

    val builder = linkBuilder().username(uuid).host(serverAddress).port(serverPort)
        .addQueryParameter("type", type).addQueryParameter("encryption", encryption)

    when (type) {
        "tcp" -> {
            if (headerType == "http") {
                builder.addQueryParameter("headerType", headerType)

                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    if (standard) {
                        builder.addQueryParameter("path", path)
                    } else {
                        builder.encodedPath(path.pathSafe())
                    }
                }
            }
        }
        "kcp" -> {
            if (headerType.isNotBlank() && headerType != "none") {
                builder.addQueryParameter("headerType", headerType)
            }
            if (mKcpSeed.isNotBlank()) {
                builder.addQueryParameter("seed", mKcpSeed)
            }
        }
        "ws", "http" -> {
            if (host.isNotBlank()) {
                builder.addQueryParameter("host", host)
            }
            if (path.isNotBlank()) {
                if (standard) {
                    builder.addQueryParameter("path", path)
                } else {
                    builder.encodedPath(path.pathSafe())
                }
            }
            if (type == "ws") {
                if (wsMaxEarlyData > 0) {
                    builder.addQueryParameter("ed", "$wsMaxEarlyData")
                    if (earlyDataHeaderName.isNotBlank()) {
                        builder.addQueryParameter("eh", earlyDataHeaderName)
                    }
                }
            }
        }
        "quic" -> {
            if (headerType.isNotBlank() && headerType != "none") {
                builder.addQueryParameter("headerType", headerType)
            }
            if (quicSecurity.isNotBlank() && quicSecurity != "none") {
                builder.addQueryParameter("quicSecurity", quicSecurity)
                builder.addQueryParameter("key", quicKey)
            }
        }
        "grpc" -> {
            if (grpcServiceName.isNotBlank()) {
                builder.addQueryParameter("serviceName", grpcServiceName)
            }
        }
    }

    if (security.isNotBlank() && security != "none") {
        builder.addQueryParameter("security", security)
        when (security) {
            "tls" -> {
                if (sni.isNotBlank()) {
                    builder.addQueryParameter("sni", sni)
                }
                if (alpn.isNotBlank()) {
                    builder.addQueryParameter("alpn", alpn)
                }
                if (certificates.isNotBlank()) {
                    builder.addQueryParameter("cert", certificates)
                }
                if (pinnedPeerCertificateChainSha256.isNotBlank()) {
                    builder.addQueryParameter("chain", pinnedPeerCertificateChainSha256)
                }
            }
        }
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink(if (this is VMessBean) "vmess" else "vless")

}
package io.nekohasekai.sagernet.fmt.socks

import cn.hutool.core.codec.Base64
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseSOCKS(link: String): SOCKSBean {
    if (!link.substringAfter("socks://").contains(":")) {
        // v2rayN shit format
        var url = link.substringAfter("socks://")
        if (url.contains("#")) {
            url = url.substringBeforeLast("#")
        }
        url = Base64.decodeStr(url)
        val httpUrl = "http://$url".toHttpUrlOrNull() ?: error("Invalid v2rayN link content: $url")
        return SOCKSBean.DEFAULT_BEAN.clone().apply {
            serverAddress = httpUrl.host
            serverPort = httpUrl.port
            username = httpUrl.username.takeIf { it != "null" } ?: ""
            password = httpUrl.password.takeIf { it != "null" } ?: ""
            if (link.contains("#")) {
                name = link.substringAfter("#").unUrlSafe()
            }
            udp = false
        }
    } else {
        val url = ("http://" + link
            .substringAfter("://"))
            .toHttpUrlOrNull() ?: error("Not supported: $link")

        return SOCKSBean().apply {
            serverAddress = url.host
            serverPort = url.port
            username = url.username
            password = url.password
            name = url.fragment
            udp = url.queryParameter("udp") == "true"
        }
    }
}

fun SOCKSBean.toUri(): String {

    val builder = HttpUrl.Builder()
        .scheme("http")
        .host(serverAddress)
        .port(serverPort)
    if (!username.isNullOrBlank()) builder.username(username)
    if (!password.isNullOrBlank()) builder.password(password)
    if (!name.isNullOrBlank()) builder.encodedFragment(name.urlSafe())
    if (udp) builder.addQueryParameter("udp", "true")
    return builder.build().toString().replaceRange(0..3, "socks")

}

fun SOCKSBean.toV2rayN(): String {

    var link = ""
    if (username.isNotBlank()) {
        link += username.urlSafe() + ":" + password.urlSafe() + "@"
    }
    link += "$serverAddress:$serverPort"
    link = "socks://" + Base64.encode(link)
    if (name.isNotBlank()) {
        link += "#" + name.urlSafe()
    }

    return link

}
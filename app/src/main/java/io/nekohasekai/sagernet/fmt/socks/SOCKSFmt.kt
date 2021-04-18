package io.nekohasekai.sagernet.fmt.socks

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseSOCKS5(link: String): SOCKSBean {
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

fun SOCKSBean.toUri(): String {

    val builder = HttpUrl.Builder()
        .scheme("http")
        .host(serverAddress)
        .port(serverPort)

    if (!username.isNullOrBlank()) builder.username(username)
    if (!password.isNullOrBlank()) builder.password(password)
    if (!name.isNullOrBlank()) builder.fragment(name)
    if (udp) builder.addQueryParameter("udp", "true")

    return builder.build().toString().replaceRange(0..4, "socks5")

}
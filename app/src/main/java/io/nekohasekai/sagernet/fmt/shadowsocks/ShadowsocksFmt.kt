package io.nekohasekai.sagernet.fmt.shadowsocks

import cn.hutool.core.codec.Base64
import com.github.shadowsocks.plugin.PluginOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

val methodsV2fly = arrayOf(
    "none",
    "aes-128-gcm",
    "aes-256-gcm",
    "chacha20-ietf-poly1305"
)

val methodsRust = arrayOf(
    "none",
    "rc4-md5",
    "aes-128-cfb",
    "aes-192-cfb",
    "aes-256-cfb",
    "aes-128-ctr",
    "aes-192-ctr",
    "aes-256-ctr",
    "bf-cfb",
    "camellia-128-cfb",
    "camellia-192-cfb",
    "camellia-256-cfb",
    "chacha20-ietf",
    "aes-128-gcm",
    "aes-256-gcm",
    "chacha20-ietf-poly1305",
    "xchacha20-ietf-poly1305",
)

fun parseShadowsocks(url: String): ShadowsocksBean {

    if (url.contains("@")) {

        // ss-android style

        val link = url.replace("ss://", "https://").toHttpUrlOrNull()
            ?: error("invalid ss-android link $url")

        if (link.password.isNotBlank()) {

            return ShadowsocksBean().apply {

                serverAddress = link.host
                serverPort = link.port
                method = link.username
                password = link.password
                plugin = link.queryParameter("plugin") ?: ""
                name = link.fragment ?: ""

            }

        }
        val methodAndPswd = Base64.decodeStr(link.username)

        return ShadowsocksBean().apply {

            serverAddress = link.host
            serverPort = link.port
            method = methodAndPswd.substringBefore(":")
            password = methodAndPswd.substringAfter(":")
            plugin = link.queryParameter("plugin") ?: ""
            name = link.fragment ?: ""

        }

    } else {

        // v2rayN style

        var v2Url = url

        if (v2Url.contains("#")) v2Url = v2Url.substringBefore("#")

        val link =
            ("https://" + Base64.decodeStr(v2Url.substringAfter("ss://"))).toHttpUrlOrNull()
                ?: error("invalid v2rayN link $url")

        return ShadowsocksBean().apply {

            serverAddress = link.host
            serverPort = link.port
            method = link.username
            password = link.password
            plugin = ""
            name = link.fragment ?: ""

        }

    }

}

fun parseShadowsocks(ssObj: JSONObject): ShadowsocksBean {
    var pluginStr = ""
    val pId = ssObj.optString("plugin")
    if (!pId.isNullOrBlank()) {
        val plugin = PluginOptions(pId, ssObj.optString("plugin_opts"))
        pluginStr = plugin.toString(false)
    }
    return ShadowsocksBean().apply {
        serverAddress = ssObj.getString("server")
        serverPort = ssObj.getInt("server_port")
        password = ssObj.getString("password")
        method = ssObj.getString("method")
        plugin = pluginStr
        name = ssObj.optString("remarks", "")
    }
}

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

package io.nekohasekai.sagernet.fmt.shadowsocks

import cn.hutool.core.codec.Base64
import cn.hutool.json.JSONObject
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginManager
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

val methodsV2fly = arrayOf(
    "none", "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305"
)

val methodsClash = arrayOf(
    "none",
    "aes-128-gcm",
    "aes-192-gcm",
    "aes-256-gcm",
    "chacha20-ietf-poly1305",
    "xchacha20-ietf-poly1305",
    "rc4",
    "rc4-md5",
    "aes-128-ctr",
    "aes-192-ctr",
    "aes-256-ctr",
    "aes-128-cfb",
    "aes-192-cfb",
    "aes-256-cfb",
    "aes-128-cfb8",
    "aes-192-cfb8",
    "aes-256-cfb8",
    "aes-128-ofb",
    "aes-192-ofb",
    "aes-256-ofb",
    "bf-cfb",
    "cast5-cfb",
    "des-cfb",
    "idea-cfb",
    "rc2-cfb",
    "seed-cfb",
    "camellia-128-cfb",
    "camellia-192-cfb",
    "camellia-256-cfb",
    "camellia-128-cfb8",
    "camellia-192-cfb8",
    "camellia-256-cfb8",
    "salsa20",
    "chacha20",
    "chacha20-ietf",
    "xchacha20",
)

val methodsSsRust = arrayOf(
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

val methodsSsLibev = arrayOf(
    "rc4-md5",
    "aes-128-gcm",
    "aes-192-gcm",
    "aes-256-gcm",
    "aes-128-cfb",
    "aes-192-cfb",
    "aes-256-cfb",
    "aes-128-ctr",
    "aes-192-ctr",
    "aes-256-ctr",
    "camellia-128-cfb",
    "camellia-192-cfb",
    "camellia-256-cfb",
    "bf-cfb",
    "chacha20-ietf-poly1305",
    "xchacha20-ietf-poly1305",
    "salsa20",
    "chacha20",
    "chacha20-ietf"
)

fun PluginConfiguration.fixInvalidParams() {

    if (selected.contains("v2ray") && selected != "v2ray-plugin") {

        pluginsOptions["v2ray-plugin"] = getOptions().apply { id = "v2ray-plugin" }
        pluginsOptions.remove(selected)
        selected = "v2ray-plugin"

        // resolve v2ray plugin

    }

    if (selected.contains("obfs") && selected != "obfs-local") {

        pluginsOptions["obfs-local"] = getOptions().apply { id = "obfs-local" }
        pluginsOptions.remove(selected)
        selected = "obfs-local"

        // resolve clash obfs

    }

    if (selected == "obfs-local") {
        val options = pluginsOptions["obfs-local"]
        if (options != null) {
            if (options.containsKey("mode")) {
                options["obfs"] = options["mode"]
                options.remove("mode")
            }
            if (options.containsKey("host")) {
                options["obfs-host"] = options["host"]
                options.remove("host")
            }
        }
    }

}

fun ShadowsocksBean.fixInvalidParams() {
    if (method == "plain") method = "none"
    plugin = PluginConfiguration(plugin).apply { fixInvalidParams() }.toString()

}

fun parseShadowsocks(url: String): ShadowsocksBean {

    if (url.contains("@")) {

        var link = url.replace("ss://", "https://").toHttpUrlOrNull() ?: error(
            "invalid ss-android link $url"
        )

        if (link.username.isBlank()) { // fix justmysocks's shit link

            link = (("https://" + url.substringAfter("ss://")
                .substringBefore("#")
                .decodeBase64UrlSafe()).toHttpUrlOrNull() ?: error(
                "invalid jms link $url"
            )).newBuilder().fragment(url.substringAfter("#")).build()
        }

        // ss-android style

        if (link.password.isNotBlank()) {

            return ShadowsocksBean().apply {

                serverAddress = link.host
                serverPort = link.port
                method = link.username
                password = link.password
                plugin = link.queryParameter("plugin") ?: ""
                name = link.fragment ?: ""

                fixInvalidParams()

            }

        }

        val methodAndPswd = link.username.decodeBase64UrlSafe()

        return ShadowsocksBean().apply {

            serverAddress = link.host
            serverPort = link.port
            method = methodAndPswd.substringBefore(":")
            password = methodAndPswd.substringAfter(":")
            plugin = link.queryParameter("plugin") ?: ""
            name = link.fragment ?: ""

            fixInvalidParams()

        }

    } else {

        // v2rayN style

        var v2Url = url

        if (v2Url.contains("#")) v2Url = v2Url.substringBefore("#")

        val link = ("https://" + v2Url.substringAfter("ss://")
            .decodeBase64UrlSafe()).toHttpUrlOrNull() ?: error("invalid v2rayN link $url")

        return ShadowsocksBean().apply {

            serverAddress = link.host
            serverPort = link.port
            method = link.username
            password = link.password
            plugin = ""
            if (url.contains("#")) {
                name = url.substringAfter("#").unUrlSafe()
            }

            fixInvalidParams()

        }

    }

}

fun ShadowsocksBean.toUri(): String {

    val builder = linkBuilder().username(Base64.encodeUrlSafe("$method:$password"))
        .host(serverAddress)
        .port(serverPort)

    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("ss").replace("$serverPort/", "$serverPort")

}

fun JSONObject.parseShadowsocks(): ShadowsocksBean {
    return ShadowsocksBean().apply {
        var pluginStr = ""
        val pId = getStr("plugin")
        if (!pId.isNullOrBlank()) {
            val plugin = PluginOptions(pId, getStr("plugin_opts"))
            pluginStr = plugin.toString(false)
        }

        serverAddress = getStr("server")
        serverPort = getInt("server_port")
        password = getStr("password")
        method = getStr("method")
        plugin = pluginStr
        name = getStr("remarks", "")

        fixInvalidParams()
    }
}


fun ShadowsocksBean.buildShadowsocksConfig(port: Int): String {
    val proxyConfig = JSONObject().also {
        it["server"] = finalAddress
        it["server_port"] = finalPort
        it["method"] = method
        it["password"] = password
        it["local_address"] = LOCALHOST
        it["local_port"] = port
        it["local_udp_address"] = LOCALHOST
        it["local_udp_port"] = port
        it["mode"] = "tcp_and_udp"
        it["ipv6_first"] = DataStore.ipv6Mode >= IPv6Mode.PREFER
        it["keep_alive"] = DataStore.tcpKeepAliveInterval
    }

    if (plugin.isNotBlank()) {
        val pluginConfiguration = PluginConfiguration(plugin ?: "")
        PluginManager.init(pluginConfiguration)?.let { (path, opts, _) ->
            proxyConfig["plugin"] = path
            proxyConfig["plugin_opts"] = opts.toString()
        }
    }

    return proxyConfig.toStringPretty()
}
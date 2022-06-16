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
import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import io.nekohasekai.sagernet.ktx.queryParameter
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import libcore.Libcore

val methodsSing = arrayOf(
    "2022-blake3-aes-128-gcm",
    "2022-blake3-aes-256-gcm",
    "2022-blake3-chacha20-poly1305"
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

}

fun ShadowsocksBean.fixInvalidParams() {
    if (method == "plain") method = "none"
    plugin = PluginConfiguration(plugin).apply { fixInvalidParams() }.toString()

}

fun parseShadowsocks(url: String): ShadowsocksBean {

    if (url.contains("@")) {

        var link = Libcore.parseURL(url)

        if (link.username.isBlank()) { // fix justmysocks's shit link
            link = Libcore.parseURL(
                ("ss://" + url.substringAfter("ss://").substringBefore("#").decodeBase64UrlSafe())
            )
            link.setRawFragment(url.substringAfter("#"))
        }

        // ss-android style

        if (link.password.isNotBlank()) {

            return ShadowsocksBean().apply {

                serverAddress = link.host
                serverPort = link.port
                method = link.username
                password = link.password
                plugin = link.queryParameter("plugin") ?: ""
                name = link.fragment
                uot = link.queryParameter("udp-over-tcp") == "true" || name.contains("SUoT")
                encryptedProtocolExtension = link.queryParameter("encrypted-protocol-extension") == "true"
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
            name = link.fragment
            uot = link.queryParameter("udp-over-tcp") == "true" || name.contains("SUoT")
            encryptedProtocolExtension = link.queryParameter("encrypted-protocol-extension") == "true"

            fixInvalidParams()

        }

    } else {

        // v2rayN style

        var v2Url = url

        if (v2Url.contains("#")) v2Url = v2Url.substringBefore("#")

        val link = Libcore.parseURL(
            ("ss://" + v2Url.substringAfter("ss://").decodeBase64UrlSafe())
        )

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

    val builder = Libcore.newURL("ss")
    builder.host = serverAddress
    builder.port = serverPort
    if (method.startsWith("2022")) {
        builder.username = method
        builder.password = password
    } else {
        builder.username = Base64.encodeUrlSafe("$method:$password")
    }

    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.setRawFragment(name.urlSafe())
    }

    if (uot) {
        builder.addQueryParameter("udp-over-tcp", "true")
    }

    if (encryptedProtocolExtension) {
        builder.addQueryParameter("encrypted-protocol-extension", "true")
    }

    return builder.string

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
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

package io.nekohasekai.sagernet.fmt.shadowsocks

import cn.hutool.core.codec.Base64
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginManager
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import cn.hutool.json.JSONObject as HSONObject

val methodsV2fly = arrayOf(
    "none",
    "aes-128-gcm",
    "aes-256-gcm",
    "chacha20-ietf-poly1305"
)

fun ShadowsocksBean.fixInvalidParams() {
    if (method == "plain") method = "none"

    val pl = PluginConfiguration(plugin)

    if (pl.selected.contains("v2ray") && pl.selected != "v2ray-plugin") {

        pl.pluginsOptions["v2ray-plugin"] = pl.getOptions().apply { id = "v2ray-plugin" }
        pl.pluginsOptions.remove(pl.selected)
        pl.selected = "v2ray-plugin"

        // resolve v2ray plugin

    }

    if (pl.selected.contains("obfs") && pl.selected != "obfs-local") {

        pl.pluginsOptions["obfs-local"] = pl.getOptions().apply { id = "obfs-local" }
        pl.pluginsOptions.remove(pl.selected)
        pl.selected = "obfs-local"

        // resolve clash obfs

    }

    if (pl.selected == "obfs-local") {
        val options = pl.pluginsOptions["obfs-local"]
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

    plugin = pl.toString()

}

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

        val link =
            ("https://" + v2Url.substringAfter("ss://").decodeBase64UrlSafe()).toHttpUrlOrNull()
                ?: error("invalid v2rayN link $url")

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

    val builder = linkBuilder()
        .username(Base64.encodeUrlSafe("$method:$password"))
        .host(serverAddress)
        .port(serverPort)

    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("ss")

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

        fixInvalidParams()
    }
}

// https://github.com/shadowsocks/shadowsocks-android/blob/39f784a9d0cd191e9b8616b0b95bb2176b0fc798/core/src/main/java/com/github/shadowsocks/bg/ProxyInstance.kt#L58
val deprecatedCiphers = arrayOf("aes-192-gcm", "chacha20", "salsa20")

fun ShadowsocksBean.buildShadowsocksConfig(port: Int): String {
    if (method in deprecatedCiphers) {
        throw IllegalArgumentException("Cipher $method is deprecated.")
    }

    val proxyConfig = HSONObject().also {
        it["server"] = serverAddress
        it["server_port"] = serverPort
        it["method"] = method
        it["password"] = password
        it["local_address"] = "127.0.0.1"
        it["local_port"] = port
        it["local_udp_address"] = "127.0.0.1"
        it["local_udp_port"] = port
        it["mode"] = "tcp_and_udp"
        if (DataStore.enableLocalDNS) {
            it["dns"] = "127.0.0.1:${DataStore.localDNSPort}"
        } else {
            it["dns"] = DataStore.remoteDNS
        }

        if (DataStore.ipv6Route && DataStore.preferIpv6) {
            it["ipv6_first"] = true
        }
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
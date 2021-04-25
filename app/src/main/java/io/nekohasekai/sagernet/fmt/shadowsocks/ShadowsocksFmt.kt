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
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

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

        // reslove v2ray plugin

    }

    if (pl.selected == "obfs") {

        pl.pluginsOptions["obfs-local"] = pl.getOptions().apply { id = "obfs-local" }
        pl.pluginsOptions.remove(pl.selected)
        pl.selected = "obfs-local"

        // reslove clash obfs

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

        val methodAndPswd = Base64.decodeStr(link.username)

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
            ("https://" + Base64.decodeStr(v2Url.substringAfter("ss://"))).toHttpUrlOrNull()
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

    val builder = HttpUrl.Builder()
        .scheme("https")
        .username(Base64.encodeUrlSafe("$method:$password"))
        .host(serverAddress)
        .port(serverPort)

    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toString().replace("https://", "ss://")

}

fun ShadowsocksBean.toV2rayN(): String {

    var url = "$method:$password@$serverAddress:$serverPort"
    url = "ss://" + Base64.encodeUrlSafe(url)
    if (name.isNotBlank()) {
        url += "#" + name.urlSafe()
    }

    return url

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
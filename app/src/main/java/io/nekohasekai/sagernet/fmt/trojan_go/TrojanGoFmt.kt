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

package io.nekohasekai.sagernet.fmt.trojan_go

import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginManager
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.shadowsocks.fixInvalidParams
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseTrojanGo(server: String): TrojanGoBean {
    val link = server.replace("trojan-go://", "https://").toHttpUrlOrNull() ?: error(
        "invalid trojan-link link $server"
    )
    return TrojanGoBean().apply {
        serverAddress = link.host
        serverPort = link.port
        password = link.username
        link.queryParameter("sni")?.let {
            sni = it
        }
        link.queryParameter("type")?.let { lType ->
            type = lType

            when (type) {
                "ws" -> {
                    link.queryParameter("host")?.let {
                        host = it
                    }
                    link.queryParameter("path")?.let {
                        path = it
                    }
                }
                else -> {
                }
            }
        }
        link.queryParameter("encryption")?.let {
            encryption = it
        }
        link.queryParameter("plugin")?.let {
            plugin = it
        }
        link.fragment.takeIf { !it.isNullOrBlank() }?.let {
            name = it
        }
    }
}

fun TrojanGoBean.toUri(): String {
    val builder = linkBuilder().username(password).host(serverAddress).port(serverPort)
    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (type.isNotBlank() && type != "original") {
        builder.addQueryParameter("type", type)

        when (type) {
            "ws" -> {
                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    builder.addQueryParameter("path", path)
                }
            }
        }
    }
    if (type.isNotBlank() && type != "none") {
        builder.addQueryParameter("encryption", encryption)
    }
    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("trojan-go")
}

fun TrojanGoBean.buildTrojanGoConfig(port: Int, chain: Boolean, index: Int): String {
    return JSONObject().also { conf ->
        conf["run_type"] = "client"
        conf["local_addr"] = "127.0.0.1"
        conf["local_port"] = port
        conf["remote_addr"] = serverAddress
        conf["remote_port"] = serverPort
        conf["password"] = JSONArray().apply {
            add(password)
        }
        conf["log_level"] = if (BuildConfig.DEBUG) 0 else 2
        if (index == 0 && DataStore.enableMux) {
            conf["mux"] = JSONObject().also {
                it["enabled"] = true
                it["concurrency"] = DataStore.muxConcurrency
            }
        }
        conf["tcp"] = JSONObject().also {
            it["prefer_ipv4"] =  DataStore.ipv6Mode <= IPv6Mode.ENABLE
        }

        when (type) {
            "original" -> {
            }
            "ws" -> conf["websocket"] = JSONObject().also {
                it["enabled"] = true
                it["host"] = host
                it["path"] = path
            }
        }

        if (sni.isNotBlank()) conf["ssl"] = JSONObject().also {
            it["sni"] = sni
        }

        when {
            encryption == "none" -> {
            }
            encryption.startsWith("ss;") -> conf["shadowsocks"] = JSONObject().also {
                it["enabled"] = true
                it["method"] = encryption.substringAfter(";").substringBefore(":")
                it["password"] = encryption.substringAfter(":")
            }
        }

        if (plugin.isNotBlank()) {
            val pluginConfiguration = PluginConfiguration(plugin ?: "")
            PluginManager.init(pluginConfiguration)?.let { (path, opts, isV2) ->
                conf["transport_plugin"] = JSONObject().also {
                    it["enabled"] = true
                    it["type"] = "shadowsocks"
                    it["command"] = path
                    it["option"] = opts.toString()
                }
            }
        }

        if (chain) conf["forward_proxy"] = JSONObject().also {
            it["enabled"] = true
            it["proxy_addr"] = "127.0.0.1"
            it["proxy_port"] = port + 1
        }
    }.toStringPretty()
}

fun buildCustomTrojanConfig(config: String, port: Int): String {
    val conf = JSONObject(config)
    conf["local_port"] = port
    return conf.toStringPretty()
}

fun JSONObject.parseTrojanGo(): TrojanGoBean {
    return TrojanGoBean().applyDefaultValues().apply {
        serverAddress = getStr("remote_addr", serverAddress)
        serverPort = getInt("remote_port", serverPort)
        when (val pass = get("password")) {
            is String -> {
                password = pass
            }
            is List<*> -> {
                password = pass[0] as String
            }
        }
        getJSONObject("ssl")?.apply {
            sni = getStr("sni", sni)
        }
        getJSONObject("websocket")?.apply {
            if (getBool("enabled", false)) {
                type = "ws"
                host = getStr("host", host)
                path = getStr("path", path)
            }
        }
        getJSONObject("shadowsocks")?.apply {
            if (getBool("enabled", false)) {
                encryption = "ss;${getStr("method", "")}:${getStr("password", "")}"
            }
        }
        getJSONObject("transport_plugin")?.apply {
            if (getBool("enabled", false)) {
                when (type) {
                    "shadowsocks" -> {
                        val pl = PluginConfiguration()
                        pl.selected = getStr("command")
                        getJSONArray("arg")?.also {
                            pl.pluginsOptions[pl.selected] = PluginOptions().also { opts ->
                                var key = ""
                                it.forEachIndexed { index, param ->
                                    if (index % 2 != 0) {
                                        key = param.toString()
                                    } else {
                                        opts[key] = param.toString()
                                    }
                                }
                            }
                        }
                        getStr("option")?.also {
                            pl.pluginsOptions[pl.selected] = PluginOptions(it)
                        }
                        pl.fixInvalidParams()
                        plugin = pl.toString()
                    }
                }
            }
        }
    }
}
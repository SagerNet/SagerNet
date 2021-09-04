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

package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.database.ProxyEntity

object TypeMap : HashMap<String, Int>() {
    init {
        this["socks"] = ProxyEntity.TYPE_SOCKS
        this["http"] = ProxyEntity.TYPE_HTTP
        this["ss"] = ProxyEntity.TYPE_SS
        this["ssr"] = ProxyEntity.TYPE_SSR
        this["vmess"] = ProxyEntity.TYPE_VMESS
        this["vless"] = ProxyEntity.TYPE_VLESS
        this["trojan"] = ProxyEntity.TYPE_TROJAN
        this["trojan-go"] = ProxyEntity.TYPE_TROJAN_GO
        this["naive"] = ProxyEntity.TYPE_NAIVE
        this["pt"] = ProxyEntity.TYPE_PING_TUNNEL
        this["rb"] = ProxyEntity.TYPE_RELAY_BATON
        this["brook"] = ProxyEntity.TYPE_BROOK
        this["config"] = ProxyEntity.TYPE_CONFIG
        this["hysteria"] = ProxyEntity.TYPE_HYSTERIA
        this["snell"] = ProxyEntity.TYPE_SNELL
        this["ssh"] = ProxyEntity.TYPE_SSH
        this["wg"] = ProxyEntity.TYPE_WG
    }

    val reversed = HashMap<Int, String>()

    init {
        TypeMap.forEach { (key, type) ->
            reversed[type] = key
        }
    }

}
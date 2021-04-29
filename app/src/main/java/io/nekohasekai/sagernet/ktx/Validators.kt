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

package io.nekohasekai.sagernet.ktx

import com.github.shadowsocks.plugin.PluginConfiguration
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean

enum class ValidateResult {
    INSECURE, DEPRECATED, SECURE
}

private val ssSecureList = "(gcm|poly1305)".toRegex()

fun AbstractBean.isInsecure(): ValidateResult {
    if (this is ShadowsocksBean) {
        if (plugin.isBlank() || PluginConfiguration(plugin).selected == "obfs-local") {
            if (!method.contains(ssSecureList)) {
                return ValidateResult.INSECURE
            }
        }
    } else if (this is ShadowsocksRBean) {
        return ValidateResult.DEPRECATED
    } else if (this is HttpBean) {
        if (!tls) return ValidateResult.INSECURE
    } else if (this is SOCKSBean) {
        if (!tls) return ValidateResult.INSECURE
    } else if (this is VMessBean) {
        if (alterId > 0) return ValidateResult.DEPRECATED
        if (security.isBlank() || security == "none") {
        }
    }
    return ValidateResult.SECURE
}
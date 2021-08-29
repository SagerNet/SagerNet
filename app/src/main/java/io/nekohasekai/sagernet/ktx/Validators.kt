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

package io.nekohasekai.sagernet.ktx

import androidx.annotation.RawRes
import cn.hutool.core.lang.Validator
import cn.hutool.core.net.NetUtil.isInnerIP
import cn.hutool.json.JSONObject
import com.github.shadowsocks.plugin.PluginConfiguration
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.internal.ConfigBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.group.RawUpdater

interface ValidateResult
object ResultSecure : ValidateResult
object ResultLocal : ValidateResult
class ResultDeprecated(@RawRes val textRes: Int) : ValidateResult
class ResultInsecure(@RawRes val textRes: Int) : ValidateResult

val ssSecureList = "(gcm|poly1305)".toRegex()

fun AbstractBean.isInsecure(): ValidateResult {
    if (Validator.isIpv4(serverAddress) && isInnerIP(serverAddress) || serverAddress in arrayOf(
            "localhost", "::"
        )
    ) {
        return ResultLocal
    }
    if (this is ShadowsocksBean) {
        if (plugin.isBlank() || PluginConfiguration(plugin).selected == "obfs-local") {
            if (!method.contains(ssSecureList)) {
                return ResultInsecure(R.raw.shadowsocks_stream_cipher)
            }
        }
    } else if (this is ShadowsocksRBean) {
        return ResultDeprecated(R.raw.shadowsocksr)
    } else if (this is HttpBean) {
        if (!tls) return ResultInsecure(R.raw.not_encrypted)
    } else if (this is SOCKSBean) {
        if (!tls) return ResultInsecure(R.raw.not_encrypted)
    } else if (this is VMessBean) {
        if (security in arrayOf("", "none")) {
            if (encryption in arrayOf("none", "zero")) {
                return ResultInsecure(R.raw.not_encrypted)
            }
        }
        if (type == "kcp" && mKcpSeed.isBlank()) {
            return ResultInsecure(R.raw.mkcp_no_seed)
        }
        if (allowInsecure) return ResultInsecure(R.raw.insecure)
        if (alterId > 0) return ResultDeprecated(R.raw.vmess_md5_auth)
    } else if (this is VLESSBean) {
        if (security in arrayOf("", "none")) {
            return ResultInsecure(R.raw.not_encrypted)
        }
        if (type == "kcp" && mKcpSeed.isBlank()) {
            return ResultInsecure(R.raw.mkcp_no_seed)
        }
        if (allowInsecure) return ResultInsecure(R.raw.insecure)
    } else if (this is ConfigBean) {
        try {
            val profiles = RawUpdater.parseJSON(JSONObject(content))
            val results = profiles.map { it.isInsecure() }
            (results.find { it is ResultInsecure } ?: results.find { it is ResultDeprecated }
            ?: results.find { it is ResultLocal })?.also {
                return it
            }
        } catch (ignored: Exception) {
        }
    }
    return ResultSecure
}
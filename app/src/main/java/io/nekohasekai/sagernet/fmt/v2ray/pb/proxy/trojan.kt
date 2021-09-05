/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.fmt.v2ray.pb.proxy

import com.v2ray.core.app.proxyman.senderConfig
import com.v2ray.core.proxy.trojan.account
import com.v2ray.core.proxy.trojan.clientConfig
import com.v2ray.core.transport.internet.streamConfig
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.pb.*

fun TrojanBean.buildProxySettings() = letTyped {
    clientConfig {
        server.add(asEndpoint(typedMessage {
            account {
                password = it.password
            }
        }))
    }
}

fun TrojanBean.buildSenderSettings() = letTyped {
    senderConfig {
        when (security) {
            //"tls" ->
            else -> {
                streamSettings = streamConfig {
                    securityType = securityTypeTls
                    tlsSettings {
                        if (sni.isNotBlank()) serverName = sni
                        if (alpn.isNotBlank()) nextProtocol.addAll(alpn.split("\n"))
                        if (it.allowInsecure) allowInsecure = true
                    }
                }
            }
        }
    }
}
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
import com.v2ray.core.proxy.http.account
import com.v2ray.core.proxy.http.clientConfig
import com.v2ray.core.transport.internet.streamConfig
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.v2ray.pb.*

fun HttpBean.buildProxySettings() = letTyped {
    clientConfig {
        server.add(asEndpoint(username.takeIf { it.isNotBlank() }?.typedMessage {
            account {
                username = it.username
                password = it.password
            }
        }))
    }
}

fun HttpBean.buildSenderSettings() = letTyped {
    senderConfig {
        if (tls) {
            streamSettings = streamConfig {
                securityType = securityTypeTls
                if (sni.isNotBlank()) tlsSettings {
                    serverName = sni
                }
            }
        }
    }
}
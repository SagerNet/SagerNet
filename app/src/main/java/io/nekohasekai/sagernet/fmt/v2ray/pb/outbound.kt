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

package io.nekohasekai.sagernet.fmt.v2ray.pb

import com.google.protobuf.Any
import com.v2ray.core.common.protocol.serverEndpoint
import com.v2ray.core.common.protocol.user
import com.v2ray.core.outboundHandlerConfig
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.pb.proxy.buildProxySettings
import io.nekohasekai.sagernet.fmt.v2ray.pb.proxy.buildSenderSettings
import io.nekohasekai.sagernet.fmt.v2ray.pb.proxy.buildStandardSenderSettings

fun AbstractBean.asEndpoint(userMessage: Any?) = serverEndpoint {
    address = finalAddress.toIpOrDomain()
    port = finalPort
    if (userMessage != null) user.add(user {
        level = defaultLevel
        email = defaultEmail
        account = userMessage
    })
}

fun ProxyEntity.buildOutbound() = outboundHandlerConfig {
    when (val bean = requireBean()) {
        is SOCKSBean -> {
            proxySettings = bean.buildProxySettings()
            senderSettings = bean.buildSenderSettings()
        }
        is HttpBean -> {
            proxySettings = bean.buildProxySettings()
            senderSettings = bean.buildSenderSettings()
        }
        is ShadowsocksBean -> {
            proxySettings = bean.buildProxySettings()
        }
        is VMessBean -> {
            proxySettings = bean.buildProxySettings()
            senderSettings = bean.buildStandardSenderSettings()
        }
        is VLESSBean -> {
            proxySettings = bean.buildProxySettings()
            senderSettings = bean.buildStandardSenderSettings()
        }
        is TrojanBean -> {
            proxySettings = bean.buildProxySettings()
            senderSettings = bean.buildSenderSettings()
        }
    }
}
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

import com.v2ray.core.common.protocol.serverEndpoint
import com.v2ray.core.proxy.shadowsocks.CipherType
import com.v2ray.core.proxy.shadowsocks.account
import com.v2ray.core.proxy.shadowsocks.clientConfig
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.v2ray.pb.asEndpoint
import io.nekohasekai.sagernet.fmt.v2ray.pb.letTyped
import io.nekohasekai.sagernet.fmt.v2ray.pb.typedMessage

fun ShadowsocksBean.buildProxySettings() = letTyped {
    clientConfig {
        server.add(asEndpoint(typedMessage {
            account {
                cipherType = CipherType.valueOf(it.method.uppercase())
                password = it.password
            }
        }))
    }
}
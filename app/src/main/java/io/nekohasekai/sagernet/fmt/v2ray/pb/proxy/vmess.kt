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

import com.v2ray.core.common.protocol.SecurityType
import com.v2ray.core.common.protocol.securityConfig
import com.v2ray.core.proxy.vmess.account
import com.v2ray.core.proxy.vmess.outbound.config
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.pb.asEndpoint
import io.nekohasekai.sagernet.fmt.v2ray.pb.letTyped
import io.nekohasekai.sagernet.fmt.v2ray.pb.typedMessage

fun VMessBean.buildProxySettings() = letTyped {
    config {
        receiver.add(asEndpoint(typedMessage {
            account {
                id = uuidOrGenerate()
                alterId = it.alterId
                securitySettings = securityConfig {
                    type = SecurityType.valueOf((encryption.takeIf { it.isNotBlank() }
                        ?: "auto").uppercase())
                }

                var experimental = ""
                if (experimentalAuthenticatedLength) {
                    experimental += "AuthenticatedLength"
                }
                if (experimentalNoTerminationSignal) {
                    experimental += "NoTerminationSignal"
                }
                if (experimental.isNotBlank()) testsEnabled = experimental
            }
        }))
    }
}
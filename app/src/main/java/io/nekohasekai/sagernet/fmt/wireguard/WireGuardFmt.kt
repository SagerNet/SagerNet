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


package io.nekohasekai.sagernet.fmt.wireguard

import cn.hutool.core.codec.Base64
import cn.hutool.core.util.HexUtil
import com.wireguard.crypto.Key
import io.nekohasekai.sagernet.ktx.wrapUri

fun WireGuardBean.buildWireGuardUapiConf(): String {

    var conf = "private_key="
    conf += Key.fromBase64(privateKey).toHex()
    conf += "\npublic_key="
    conf += Key.fromBase64(peerPublicKey).toHex()
    if (peerPreSharedKey.isNotBlank()) {
        conf += "\npreshared_key="
        conf += HexUtil.encodeHexStr(Base64.decode(peerPreSharedKey))
    }
    conf += "\nendpoint=${wrapUri()}"
    conf += "\nallowed_ip=0.0.0.0/0"
    conf += "\nallowed_ip=::/0"
    return conf
}
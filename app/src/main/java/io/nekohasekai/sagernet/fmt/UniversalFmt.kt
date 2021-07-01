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

package io.nekohasekai.sagernet.fmt

import cn.hutool.core.codec.Base64Decoder
import cn.hutool.core.codec.Base64Encoder
import io.nekohasekai.sagernet.database.ProxyEntity

fun parseUniversal(link: String): AbstractBean {
    val type = link.substringAfter("sn://").substringBefore(":")
    return ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
        putByteArray(Base64Decoder.decode(link.substringAfter(":").substringAfter(":")))
    }.requireBean()
}

fun AbstractBean.toUniversalLink(): String {
    var link = "sn://"
    link += TypeMap.reversed[ProxyEntity().putBean(this).type]
    link += ":"
    link += Base64Encoder.encodeUrlSafe(KryoConverters.serialize(this))
    return link
}

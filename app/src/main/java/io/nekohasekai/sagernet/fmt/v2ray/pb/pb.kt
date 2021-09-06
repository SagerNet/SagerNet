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

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.google.protobuf.any
import com.v2ray.core.common.net.iPOrDomain
import com.v2ray.core.common.net.portRange
import io.nekohasekai.sagernet.ktx.isIpAddress
import libcore.Libcore

fun String.toIpOrDomain() = let {
    iPOrDomain {
        if (isIpAddress()) {
            ip = ByteString.copyFrom(Libcore.parseIP(it))
        } else {
            domain = it
        }
    }
}

fun Int.toPortRange() = let {
    portRange {
        from = it
        to = it
    }
}

fun typedMessage(block: () -> Message) = block().let {
    any {
        typeUrl = "types.v2fly.org/" + it.descriptorForType.fullName
        value = it.toByteString()
    }
}


fun <T> T.typedMessage(block: T.() -> Message) = block().let {
    any {
        typeUrl = "types.v2fly.org/" + it.descriptorForType.fullName
        value = it.toByteString()
    }
}

fun <T> T.letTyped(block: (T) -> Message) = block(this).let {
    any {
        typeUrl = "types.v2fly.org/" + it.descriptorForType.fullName
        value = it.toByteString()
    }
}
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

@file:Suppress("SpellCheckingInspection")

package io.nekohasekai.sagernet.ktx

import cn.hutool.core.lang.Validator
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.fmt.AbstractBean
import libcore.URL
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

fun URL.queryParameter(key: String) = queryParameterNotBlank(key).takeIf { it.isNotBlank() }
var URL.pathSegments: List<String>
    get() = path.split("/").filter { it.isNotBlank() }
    set(value) {
        path = value.joinToString("/")
    }

fun URL.addPathSegments(vararg segments: String) {
    pathSegments = pathSegments.toMutableList().apply {
        addAll(segments)
    }
}

fun String.isIpAddress(): Boolean {
    return Validator.isIpv4(this) || Validator.isIpv6(this)
}

fun String.unwrapHost(): String {
    if (startsWith("[") && endsWith("]")) {
        return substring(1, length - 1).unwrapHost()
    }
    return this
}

fun AbstractBean.wrapUri(): String {
    return if (Validator.isIpv6(finalAddress)) {
        "[$finalAddress]:$finalPort"
    } else {
        "$finalAddress:$finalPort"
    }
}

fun AbstractBean.wrapUriWithOriginHost(): String {
    return if (Validator.isIpv6(serverAddress)) {
        "[$serverAddress]:$finalPort"
    } else {
        "$serverAddress:$finalPort"
    }
}

fun AbstractBean.wrapOriginUri(): String {
    return if (Validator.isIpv6(serverAddress)) {
        "[$serverAddress]:$serverPort"
    } else {
        "$serverAddress:$serverPort"
    }
}

fun parseAddress(addressArray: ByteArray) = InetAddress.getByAddress(addressArray)
val INET_TUN = InetAddress.getByName(VpnService.PRIVATE_VLAN4_CLIENT)
val INET6_TUN = InetAddress.getByName(VpnService.PRIVATE_VLAN6_CLIENT)

fun mkPort(): Int {
    val socket = Socket()
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(0))
    val port = socket.localPort
    socket.close()
    return port
}

const val IPPROTO_ICMP = 1
const val IPPROTO_ICMPv6 = 58

const val IPPROTO_TCP = 6
const val IPPROTO_UDP = 17

const val USER_AGENT = "curl/7.74.0"
const val USER_AGENT_ORIGIN = "SagerNet/${BuildConfig.VERSION_NAME}"
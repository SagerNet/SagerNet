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

package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TrafficStats(
    // Bytes per second
    var txrateProxy: Long = 0L,
    var rxrateProxy: Long = 0L,
    var txrateDirect: Long = 0L,
    var rxrateDirect: Long = 0L,

    // Bytes for the current session
    // Outbound "bypass" usage is not counted
    var txTotal: Long = 0L,
    var rxTotal: Long = 0L,
) : Parcelable {
    operator fun plus(other: TrafficStats) = TrafficStats(
        txrateProxy + other.txrateProxy, rxrateProxy + other.rxrateProxy,
        txrateDirect + other.txrateDirect, rxrateDirect + other.rxrateDirect,
        txTotal + other.txTotal, rxTotal + other.rxTotal)
}

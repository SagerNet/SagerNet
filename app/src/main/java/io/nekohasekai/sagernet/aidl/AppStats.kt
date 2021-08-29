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

package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import io.nekohasekai.sagernet.database.StatsEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppStats(
    var packageName: String,
    var uid: Int,
    var tcpConnections: Int,
    var udpConnections: Int,
    var tcpConnectionsTotal: Int,
    var udpConnectionsTotal: Int,
    var uplink: Long,
    var downlink: Long,
    var uplinkTotal: Long,
    var downlinkTotal: Long,
    var deactivateAt: Int
) : Parcelable {

   operator fun plusAssign(stats: StatsEntity) {
       tcpConnectionsTotal += stats.tcpConnections
       udpConnectionsTotal += stats.udpConnections
       uplinkTotal += stats.uplink
       downlinkTotal += stats.downlink
   }

}
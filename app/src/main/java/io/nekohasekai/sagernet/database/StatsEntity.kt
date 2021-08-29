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

package io.nekohasekai.sagernet.database

import android.os.Parcelable
import androidx.room.*
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "stats", indices = [Index(
        "packageName", unique = true
    )]
)
@Parcelize
class StatsEntity(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    var packageName: String = "",
    var tcpConnections: Int = 0,
    var udpConnections: Int = 0,
    var uplink: Long = 0L,
    var downlink: Long = 0L
) : Parcelable {

    fun toStats(): AppStats {
        return AppStats(
            packageName,
            PackageCache[packageName] ?: 1000,
            0,
            0,
            tcpConnections,
            udpConnections,
            0,
            0,
            uplink,
            downlink,
            0
        )
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM stats")
        fun all(): List<StatsEntity>

        @Query("SELECT * FROM stats WHERE packageName = :packageName")
        operator fun get(packageName: String): StatsEntity?

        @Query("DELETE FROM stats WHERE packageName = :packageName")
        fun delete(packageName: String): Int

        @Insert
        fun create(stats: StatsEntity)

        @Update
        fun update(stats: List<StatsEntity>)

        @Query("DELETE FROM stats")
        fun deleteAll()

    }

}
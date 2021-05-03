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

package io.nekohasekai.sagernet.database

import android.os.Parcelable
import androidx.room.*
import kotlinx.parcelize.Parcelize

@Entity(tableName = "rules")
@Parcelize
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    var name: String = "",
    var userOrder: Long = 0L,
    var enabled: Boolean = true,
    var domains: String = "",
    var ip: String = "",
    var port: String = "",
    var sourcePort: String = "",
    var network: String = "",
    var source: String = "",
    var protocol: String = "",
    var attrs: String = "",
    var outbound: Long = 0,
) : Parcelable {

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM rules ORDER BY userOrder")
        fun allRules(): List<RuleEntity>

        @Query("SELECT MAX(userOrder) + 1 FROM rules")
        fun nextOrder(): Long?

        @Query("SELECT * FROM rules WHERE id = :ruleId")
        fun getById(ruleId: Long): RuleEntity?

        @Query("DELETE FROM rules WHERE id = :ruleId")
        fun deleteById(ruleId: Long): Int

        @Delete
        fun deleteRule(vararg group: RuleEntity)

        @Insert
        fun createRule(group: RuleEntity): Long

        @Update
        fun updateRule(group: RuleEntity)

    }


}
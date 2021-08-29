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
import io.nekohasekai.sagernet.AppStatus
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app
import kotlinx.parcelize.Parcelize

@Entity(tableName = "rules")
@Parcelize
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var name: String = "",
    var userOrder: Long = 0L,
    var enabled: Boolean = false,
    var domains: String = "",
    var ip: String = "",
    var port: String = "",
    var sourcePort: String = "",
    var network: String = "",
    var source: String = "",
    var protocol: String = "",
    var attrs: String = "",
    var outbound: Long = 0,
    var reverse: Boolean = false,
    var redirect: String = "",
    var packages: List<String> = listOf(),
    var appStatus: List<String> = listOf(),
) : Parcelable {

    fun isBypassRule(): Boolean {
        return (domains.isNotBlank() && ip.isBlank() || ip.isNotBlank() && domains.isBlank()) && port.isBlank() && sourcePort.isBlank() && network.isBlank() && source.isBlank() && protocol.isBlank() && attrs.isBlank() && !reverse && redirect.isBlank() && outbound == -1L && packages.isEmpty() && appStatus.isEmpty()
    }

    fun displayName(): String {
        return name.takeIf { it.isNotBlank() } ?: "Rule $id"
    }

    fun mkSummary(): String {
        var summary = ""
        if (domains.isNotBlank()) summary += "$domains\n"
        if (ip.isNotBlank()) summary += "$ip\n"
        if (sourcePort.isNotBlank()) summary += "$sourcePort\n"
        if (network.isNotBlank()) summary += "$network\n"
        if (source.isNotBlank()) summary += "$source\n"
        if (protocol.isNotBlank()) summary += "$protocol\n"
        if (attrs.isNotBlank()) summary += "$attrs\n"
        if (reverse) summary += "$redirect\n"
        if (packages.isNotEmpty()) summary += app.getString(
            R.string.apps_message, packages.size
        ) + "\n"
        if (appStatus.isNotEmpty()) summary += displayAppStatus().joinToString("\n")
        val lines = summary.trim().split("\n")
        return if (lines.size > 3) {
            lines.subList(0, 3).joinToString("\n", postfix = "\n...")
        } else {
            summary.trim()
        }
    }

    fun displayOutbound(): String {
        if (reverse) {
            return app.getString(R.string.route_reverse)
        }
        return when (outbound) {
            0L -> app.getString(R.string.route_proxy)
            -1L -> app.getString(R.string.route_bypass)
            -2L -> app.getString(R.string.route_block)
            else -> ProfileManager.getProfile(outbound)?.displayName()
                ?: app.getString(R.string.route_proxy)
        }
    }

    fun displayAppStatus(): List<String> {
        return appStatus.map {
            when (it) {
                AppStatus.FOREGROUND -> app.getString(R.string.foreground)
                /*AppStatus.BACKGROUND*/ else -> app.getString(R.string.background)
            }
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * from rules WHERE (appStatus != '' OR packages != '') AND enabled = 1")
        fun checkVpnNeeded(): List<RuleEntity>

        @Query("SELECT * FROM rules ORDER BY userOrder")
        fun allRules(): List<RuleEntity>

        @Query("SELECT * FROM rules WHERE enabled = :enabled ORDER BY userOrder")
        fun enabledRules(enabled: Boolean = true): List<RuleEntity>

        @Query("SELECT MAX(userOrder) + 1 FROM rules")
        fun nextOrder(): Long?

        @Query("SELECT * FROM rules WHERE id = :ruleId")
        fun getById(ruleId: Long): RuleEntity?

        @Query("DELETE FROM rules WHERE id = :ruleId")
        fun deleteById(ruleId: Long): Int

        @Delete
        fun deleteRule(rule: RuleEntity)

        @Delete
        fun deleteRules(rules: List<RuleEntity>)

        @Insert
        fun createRule(rule: RuleEntity): Long

        @Update
        fun updateRule(rule: RuleEntity)

        @Update
        fun updateRules(rules: List<RuleEntity>)

        @Query("DELETE FROM rules")
        fun deleteAll()

    }


}
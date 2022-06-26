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

import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.gson.GsonConverters
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Database(
    entities = [ProxyGroup::class, ProxyEntity::class, RuleEntity::class, StatsEntity::class],
    version = 17,
    autoMigrations = [AutoMigration(
        from = 12,
        to = 14,
    ), AutoMigration(
        from = 14, to = 15, spec = SagerDatabase_Migration_14_15::class
    ), AutoMigration(
        from = 15,
        to = 16,
    ), AutoMigration(
        from = 16,
        to = 17,
    ), AutoMigration(
        from = 15,
        to = 17,
    )]
)
@TypeConverters(value = [KryoConverters::class, GsonConverters::class])
abstract class SagerDatabase : RoomDatabase() {

    companion object {
        @Suppress("EXPERIMENTAL_API_USAGE")
        private val instance by lazy {
            SagerNet.application.getDatabasePath(Key.DB_PROFILE).parentFile?.mkdirs()
            Room.databaseBuilder(SagerNet.application, SagerDatabase::class.java, Key.DB_PROFILE)
                .addMigrations(
                    SagerDatabase_Migration_1_2,
                    SagerDatabase_Migration_2_3,
                    SagerDatabase_Migration_3_4,
                    SagerDatabase_Migration_4_5,
                    SagerDatabase_Migration_5_6,
                    SagerDatabase_Migration_6_7,
                    SagerDatabase_Migration_7_8,
                    SagerDatabase_Migration_8_9,
                    SagerDatabase_Migration_9_10,
                    SagerDatabase_Migration_10_11,
                    SagerDatabase_Migration_11_12
                )
                .fallbackToDestructiveMigrationOnDowngrade()
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .setQueryExecutor { GlobalScope.launch { it.run() } }
                .build()
        }

        val groupDao get() = instance.groupDao()
        val proxyDao get() = instance.proxyDao()
        val rulesDao get() = instance.rulesDao()
        val statsDao get() = instance.statsDao()

    }

    abstract fun groupDao(): ProxyGroup.Dao
    abstract fun proxyDao(): ProxyEntity.Dao
    abstract fun rulesDao(): RuleEntity.Dao
    abstract fun statsDao(): StatsEntity.Dao

}

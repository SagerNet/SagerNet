@file:Suppress("ClassName")

package io.nekohasekai.sagernet.database

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SagerDatabase_Migration_1_2 : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""ALTER TABLE `rules` ADD `packages` TEXT NOT NULL DEFAULT ''""")
    }
}

object SagerDatabase_Migration_2_3 : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""ALTER TABLE `proxy_entities` ADD `hysteriaBean` BLOB DEFAULT NULL""")
    }
}

object SagerDatabase_Migration_3_4 : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""ALTER TABLE `proxy_entities` ADD `snellBean` BLOB DEFAULT NULL""")
    }
}

object SagerDatabase_Migration_4_5 : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""ALTER TABLE `proxy_groups` ADD `order` INTEGER NOT NULL DEFAULT 0""")
    }
}

object SagerDatabase_Migration_5_6 : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""CREATE TABLE IF NOT EXISTS `stats` (`packageName` TEXT NOT NULL, `tcpConnections` INTEGER NOT NULL, `udpConnections` INTEGER NOT NULL, `uplink` INTEGER NOT NULL, `downlink` INTEGER NOT NULL, PRIMARY KEY(`packageName`))""")
    }
}

object SagerDatabase_Migration_6_7 : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""CREATE TABLE IF NOT EXISTS `stats_MERGE_TABLE` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `tcpConnections` INTEGER NOT NULL, `udpConnections` INTEGER NOT NULL, `uplink` INTEGER NOT NULL, `downlink` INTEGER NOT NULL)""")
        database.execSQL("""INSERT INTO `stats_MERGE_TABLE` (`packageName`,`tcpConnections`,`udpConnections`,`uplink`,`downlink`,`id`) SELECT `stats`.`packageName`,`stats`.`tcpConnections`,`stats`.`udpConnections`,`stats`.`uplink`,`stats`.`downlink`,0 FROM `stats`""")
        database.execSQL("""DROP TABLE IF EXISTS `stats`""")
        database.execSQL("""ALTER TABLE `stats_MERGE_TABLE` RENAME TO `stats`""")
        database.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_stats_packageName` ON `stats` (`packageName`)""")
        database.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_stats_packageName` ON `stats` (`packageName`)""")
    }
}

object SagerDatabase_Migration_7_8 : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""ALTER TABLE `rules` ADD `appStatus` TEXT NOT NULL DEFAULT ''""")
    }
}

object SagerDatabase_Migration_8_9 : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""ALTER TABLE `proxy_entities` ADD `sshBean` BLOB DEFAULT NULL""")
    }
}

object SagerDatabase_Migration_9_10 : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""ALTER TABLE `proxy_entities` ADD `wgBean` BLOB DEFAULT NULL""")
    }
}

object SagerDatabase_Migration_10_11 : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""CREATE TABLE IF NOT EXISTS `proxy_entities_MERGE_TABLE` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupId` INTEGER NOT NULL, `type` INTEGER NOT NULL, `userOrder` INTEGER NOT NULL, `tx` INTEGER NOT NULL, `rx` INTEGER NOT NULL, `status` INTEGER NOT NULL, `ping` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `error` TEXT, `socksBean` BLOB, `httpBean` BLOB, `ssBean` BLOB, `ssrBean` BLOB, `vmessBean` BLOB, `vlessBean` BLOB, `trojanBean` BLOB, `trojanGoBean` BLOB, `naiveBean` BLOB, `ptBean` BLOB, `rbBean` BLOB, `brookBean` BLOB, `hysteriaBean` BLOB, `sshBean` BLOB, `wgBean` BLOB, `configBean` BLOB, `chainBean` BLOB, `balancerBean` BLOB)""")
        database.execSQL(
            """INSERT INTO `proxy_entities_MERGE_TABLE` (`id`,`groupId`,`type`,`userOrder`,`tx`,`rx`,`status`,`ping`,`uuid`,`error`,`socksBean`,`httpBean`,`ssBean`,`ssrBean`,`vmessBean`,`vlessBean`,`trojanBean`,`trojanGoBean`,`naiveBean`,`ptBean`,`rbBean`,`brookBean`,`hysteriaBean`,`sshBean`,`wgBean`,`configBean`,`chainBean`,`balancerBean`) SELECT `proxy_entities`.`id`,`proxy_entities`.`groupId`,`proxy_entities`.`type`,`proxy_entities`.`userOrder`,`proxy_entities`.`tx`,`proxy_entities`.`rx`,`proxy_entities`.`status`,`proxy_entities`.`ping`,`proxy_entities`.`uuid`,`proxy_entities`.`error`,`proxy_entities`.`socksBean`,`proxy_entities`.`httpBean`,`proxy_entities`.`ssBean`,`proxy_entities`.`ssrBean`,`proxy_entities`.`vmessBean`,`proxy_entities`.`vlessBean`,`proxy_entities`.`trojanBean`,`proxy_entities`.`trojanGoBean`,`proxy_entities`.`naiveBean`,`proxy_entities`.`ptBean`,`proxy_entities`.`rbBean`,`proxy_entities`.`brookBean`,`proxy_entities`.`hysteriaBean`,`proxy_entities`.`sshBean`,`proxy_entities`.`wgBean`,`proxy_entities`.`configBean`,`proxy_entities`.`chainBean`,`proxy_entities`.`balancerBean` FROM `proxy_entities`"""
        )
        database.execSQL("""DROP TABLE IF EXISTS `proxy_entities`""")
        database.execSQL("""ALTER TABLE `proxy_entities_MERGE_TABLE` RENAME TO `proxy_entities`""")
        database.execSQL("""CREATE INDEX IF NOT EXISTS `groupId` ON `proxy_entities` (`groupId`)""")
    }
}

object SagerDatabase_Migration_11_12 : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase): Unit {
        database.execSQL("""CREATE TABLE IF NOT EXISTS `rules_MERGE_TABLE` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `userOrder` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `domains` TEXT NOT NULL, `ip` TEXT NOT NULL, `port` TEXT NOT NULL, `sourcePort` TEXT NOT NULL, `network` TEXT NOT NULL, `source` TEXT NOT NULL, `protocol` TEXT NOT NULL, `attrs` TEXT NOT NULL, `outbound` INTEGER NOT NULL, `reverse` INTEGER NOT NULL, `redirect` TEXT NOT NULL, `packages` TEXT NOT NULL)""")
        database.execSQL("""INSERT INTO `rules_MERGE_TABLE` (`id`,`name`,`userOrder`,`enabled`,`domains`,`ip`,`port`,`sourcePort`,`network`,`source`,`protocol`,`attrs`,`outbound`,`reverse`,`redirect`,`packages`) SELECT `rules`.`id`,`rules`.`name`,`rules`.`userOrder`,`rules`.`enabled`,`rules`.`domains`,`rules`.`ip`,`rules`.`port`,`rules`.`sourcePort`,`rules`.`network`,`rules`.`source`,`rules`.`protocol`,`rules`.`attrs`,`rules`.`outbound`,`rules`.`reverse`,`rules`.`redirect`,`rules`.`packages` FROM `rules`""")
        database.execSQL("""DROP TABLE IF EXISTS `rules`""")
        database.execSQL("""ALTER TABLE `rules_MERGE_TABLE` RENAME TO `rules`""")
    }
}

@DeleteTable(
    tableName = "KeyValuePair"
)
class SagerDatabase_Migration_14_15 : AutoMigrationSpec
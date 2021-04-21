package io.nekohasekai.sagernet.database

import androidx.room.*
import java.util.*

@Entity(tableName = "proxy_groups")
class ProxyGroup(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    var userOrder: Long = 0L,
    var isDefault: Boolean = false,
    var name: String? = null,
    var isSubscription: Boolean = false,
    var subscriptionLinks: MutableList<String> = LinkedList(),
    var lastUpdate: Long = 0L,
    var layout: Int = 0,
) {

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM proxy_groups ORDER BY userOrder")
        fun allGroups(): List<ProxyGroup>

        @Query("SELECT * FROM proxy_groups WHERE id = :groupId")
        fun getById(groupId: Long): ProxyGroup?

        @Delete
        fun delete(group: ProxyGroup)

        @Insert
        fun createGroup(group: ProxyGroup): Long

    }

}
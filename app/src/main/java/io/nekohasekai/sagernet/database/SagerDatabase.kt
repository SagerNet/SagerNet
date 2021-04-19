package io.nekohasekai.sagernet.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.matrix.roomigrant.GenerateRoomMigrations
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerApp
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.gson.GsonConverters
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Database(entities = [ProxyGroup::class, ProxyEntity::class, KeyValuePair::class], version = 1)
@TypeConverters(value = [KryoConverters::class, GsonConverters::class])
@GenerateRoomMigrations
abstract class SagerDatabase : RoomDatabase() {

    companion object {
        private val instance by lazy {
            Room.databaseBuilder(SagerApp.application, SagerDatabase::class.java, Key.DB_PROFILE)
                .addMigrations(*SagerDatabase_Migrations.build())
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .setQueryExecutor { GlobalScope.launch { it.run() } }
                .build()
        }

        val profileCacheDao get() = instance.profileCacheDao()
        val groupDao get() = instance.groupDao()
        val proxyDao get() = instance.proxyDao()

    }

    abstract fun profileCacheDao(): KeyValuePair.Dao
    abstract fun groupDao(): ProxyGroup.Dao
    abstract fun proxyDao(): ProxyEntity.Dao

}

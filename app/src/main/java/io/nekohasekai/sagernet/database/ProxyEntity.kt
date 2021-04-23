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

import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.methodsV2fly
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity

@Entity(tableName = "proxy_entities", indices = [
    Index("groupId", name = "groupId")
])
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    var groupId: Long,
    var type: Int = 0,
    var userOrder: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
    var vmessBean: VMessBean? = null,
    var socksBean: SOCKSBean? = null,
    var ssBean: ShadowsocksBean? = null,
) : Parcelable {

    @Ignore
    @Transient
    var dirty: Boolean = false

    @Ignore
    @Transient
    var stats: TrafficStats? = null


    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong()) {
        dirty = parcel.readByte() > 0
        val byteArray = ByteArray(parcel.readInt())
        parcel.readByteArray(byteArray)
        when (type) {
            0 -> socksBean = KryoConverters.socksDeserialize(byteArray)
            1 -> ssBean = KryoConverters.shadowsocksDeserialize(byteArray)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(groupId)
        parcel.writeInt(type)
        parcel.writeLong(userOrder)
        parcel.writeLong(tx)
        parcel.writeLong(rx)
        parcel.writeByte(if (dirty) 1 else 0)
        val byteArray = KryoConverters.serialize(requireBean())
        parcel.writeInt(byteArray.size)
        parcel.writeByteArray(byteArray)
    }

    fun displayType(): String {
        return when (type) {
            //     2 -> "VMess"
            0 -> "SOCKS5"
            1 -> "Shadowsocks"
            else -> "Undefined type $type"
        }
    }

    fun displayName(): String {
        return requireBean().name.takeIf { !it.isNullOrBlank() }
            ?: "${requireBean().serverAddress}:${requireBean().serverPort}"
    }

    fun requireBean(): AbstractBean {
        return when (type) {
            // 2 -> vmessBean ?: error("Null vmess node")
            0 -> socksBean ?: error("Null socks node")
            1 -> ssBean ?: error("Null ss node")
            else -> error("Undefined type $type")
        }
    }

    fun useExternalShadowsocks(): Boolean {
        if (type != 1) return false
        val bean = requireSS()
        if (bean.plugin.isNotBlank()) {
            Logs.d("Requiring plugin ${bean.plugin}")
            return true
        }
        if (bean.method !in methodsV2fly) return true
        if (DataStore.forceShadowsocksRust) return true
        return false
    }

    fun putBean(bean: AbstractBean) {
        when (bean) {
            is SOCKSBean -> {
                type = 0
                socksBean = bean
            }
            is ShadowsocksBean -> {
                type = 1
                ssBean = bean
            }
            /*is VMessBean -> {
                type = 2
                vmessBean = bean
            }*/
            else -> error("Undefined type $type")
        }
    }

    fun requireVMess() = requireBean() as VMessBean
    fun requireSOCKS() = requireBean() as SOCKSBean
    fun requireSS() = requireBean() as ShadowsocksBean

    fun settingIntent(ctx: Context): Intent {
        return Intent(ctx, when (type) {
            0 -> SocksSettingsActivity::class.java
            1 -> ShadowsocksSettingsActivity::class.java
            else -> throw IllegalArgumentException()
        }).apply {
            putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, id)
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT id FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getIdsByGroup(groupId: Long): List<Long>

        @Query("SELECT * FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getByGroup(groupId: Long): List<ProxyEntity>

        @Query("SELECT COUNT(*) FROM proxy_entities WHERE groupId = :groupId")
        fun countByGroup(groupId: Long): Long

        @Query("SELECT  MAX(userOrder) + 1 FROM proxy_entities WHERE groupId = :groupId")
        fun nextOrder(groupId: Long): Long?

        @Query("SELECT * FROM proxy_entities WHERE id = :proxyId")
        fun getById(proxyId: Long): ProxyEntity?

        @Query("DELETE FROM proxy_entities WHERE id IN (:proxyId)")
        fun deleteById(proxyId: Long): Int

        @Delete
        fun deleteProxy(vararg proxy: ProxyEntity)

        @Update
        fun updateProxy(vararg proxy: ProxyEntity)

        @Insert
        fun addProxy(proxy: ProxyEntity): Long


        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        fun deleteAll(groupId: Long): Int

    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ProxyEntity> {
        override fun createFromParcel(parcel: Parcel): ProxyEntity {
            return ProxyEntity(parcel)
        }

        override fun newArray(size: Int): Array<ProxyEntity?> {
            return arrayOfNulls(size)
        }
    }
}
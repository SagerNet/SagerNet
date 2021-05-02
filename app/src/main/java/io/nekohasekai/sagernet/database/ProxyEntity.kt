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
import cn.hutool.core.lang.Validator
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http.toUri
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.methodsV2fly
import io.nekohasekai.sagernet.fmt.shadowsocks.toUri
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.toUri
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.toUri
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan.toUri
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.trojan_go.toUri
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.toUri
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ui.profile.*

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
    var socksBean: SOCKSBean? = null,
    var httpBean: HttpBean? = null,
    var ssBean: ShadowsocksBean? = null,
    var ssrBean: ShadowsocksRBean? = null,
    var vmessBean: VMessBean? = null,
    var vlessBean: VLESSBean? = null,
    var trojanBean: TrojanBean? = null,
    var trojanGoBean: TrojanGoBean? = null,
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
            2 -> ssrBean = KryoConverters.shadowsocksRDeserialize(byteArray)
            3 -> vmessBean = KryoConverters.vmessDeserialize(byteArray)
            4 -> vlessBean = KryoConverters.vlessDeserialize(byteArray)
            5 -> trojanBean = KryoConverters.trojanDeserialize(byteArray)
            6 -> httpBean = KryoConverters.httpDeserialize(byteArray)
            7 -> trojanGoBean = KryoConverters.trojanGoDeserialize(byteArray)
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
            0 -> "SOCKS5"
            1 -> "Shadowsocks"
            2 -> "ShadowsocksR"
            3 -> "VMess"
            4 -> "VLESS"
            5 -> "Trojan"
            6 -> if (requireHttp().tls) "HTTPS" else "HTTP"
            7 -> "Trojan-Go"
            else -> "Undefined type $type"
        }
    }

    fun displayName(): String {
        return requireBean().name.takeIf { !it.isNullOrBlank() }
            ?: "${requireBean().serverAddress}:${requireBean().serverPort}"
    }

    fun urlFixed(): String {
        val bean = requireBean()
        return if (Validator.isIpv6(bean.serverAddress)) {
            "[${bean.serverAddress}]:${bean.serverPort}"
        } else {
            "${bean.serverAddress}:${bean.serverPort}"
        }
    }

    fun requireBean(): AbstractBean {
        return when (type) {
            // 2 -> vmessBean ?: error("Null vmess node")
            0 -> socksBean ?: error("Null socks node")
            1 -> ssBean ?: error("Null ss node")
            2 -> ssrBean ?: error("Null ssr node")
            3 -> vmessBean ?: error("Null vmess node")
            4 -> vlessBean ?: error("Null vless node")
            5 -> trojanBean ?: error("Null trojan node")
            6 -> httpBean ?: error("Null http node")
            7 -> trojanGoBean ?: error("Null trojan-go node")
            else -> error("Undefined type $type")
        }
    }

    fun toUri(): String {
        return when (type) {
            0 -> requireSOCKS().toUri()
            1 -> requireSS().toUri()
            2 -> requireSSR().toUri()
            3 -> requireVMess().toUri(true)
            4 -> requireVLESS().toUri(true)
            5 -> requireTrojan().toUri()
            6 -> requireHttp().toUri()
            7 -> requireTrojanGo().toUri()
            else -> error("Undefined type $type")
        }
    }

    fun useExternalShadowsocks(): Boolean {
        if (type != 1) return false
        if (DataStore.forceShadowsocksRust) return true
        val bean = requireSS()
        if (bean.plugin.isNotBlank()) {
            Logs.d("Requiring plugin ${bean.plugin}")
            return true
        }
        if (bean.method !in methodsV2fly) return true
        return false
    }

    fun useXray(): Boolean {
        when (val bean = requireBean()) {
            is VLESSBean -> {
                if (bean.security == "xtls") return true
            }
            is TrojanBean -> {
                if (bean.security == "xtls") return true
            }
        }

        return false
    }

    fun putBean(bean: AbstractBean) {
        when (bean) {
            is SOCKSBean -> {
                type = 0
                socksBean = bean
            }
            is HttpBean -> {
                type = 6
                httpBean = bean
            }
            is ShadowsocksBean -> {
                type = 1
                ssBean = bean
            }
            is ShadowsocksRBean -> {
                type = 2
                ssrBean = bean
            }
            is VMessBean -> {
                type = 3
                vmessBean = bean
            }
            is VLESSBean -> {
                type = 4
                vlessBean = bean
            }
            is TrojanBean -> {
                type = 5
                trojanBean = bean
            }
            is TrojanGoBean -> {
                type = 7
                trojanGoBean = bean
            }
            else -> error("Undefined type $type")
        }
    }

    fun requireSOCKS() = requireBean() as SOCKSBean
    fun requireSS() = requireBean() as ShadowsocksBean
    fun requireSSR() = requireBean() as ShadowsocksRBean
    fun requireVMess() = requireBean() as VMessBean
    fun requireVLESS() = requireBean() as VLESSBean
    fun requireTrojan() = requireBean() as TrojanBean
    fun requireHttp() = requireBean() as HttpBean
    fun requireTrojanGo() = requireBean() as TrojanGoBean

    fun settingIntent(ctx: Context, isSubscription: Boolean): Intent {
        return Intent(ctx, when (type) {
            0 -> SocksSettingsActivity::class.java
            1 -> ShadowsocksSettingsActivity::class.java
            2 -> ShadowsocksRSettingsActivity::class.java
            3 -> VMessSettingsActivity::class.java
            4 -> VLESSSettingsActivity::class.java
            5 -> TrojanSettingsActivity::class.java
            6 -> HttpSettingsActivity::class.java
            7 -> TrojanGoSettingsActivity::class.java
            else -> throw IllegalArgumentException()
        }).apply {
            putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, id)
            putExtra(ProfileSettingsActivity.EXTRA_IS_SUBSCRIPTION, isSubscription)
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

        @Query("DELETE FROM proxy_entities WHERE groupId in (:groupId)")
        fun deleteByGroup(vararg groupId: Long)

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
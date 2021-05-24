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
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.brook.BrookBean
import io.nekohasekai.sagernet.fmt.brook.toUri
import io.nekohasekai.sagernet.fmt.chain.ChainBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http.toUri
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.toUri
import io.nekohasekai.sagernet.fmt.pingtunnel.PingTunnelBean
import io.nekohasekai.sagernet.fmt.pingtunnel.toUri
import io.nekohasekai.sagernet.fmt.relaybaton.RelayBatonBean
import io.nekohasekai.sagernet.fmt.relaybaton.toUri
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
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.toUri
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ui.profile.*

@Entity(
    tableName = "proxy_entities", indices = [
        Index("groupId", name = "groupId")
    ]
)
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
    var naiveBean: NaiveBean? = null,
    var ptBean: PingTunnelBean? = null,
    var rbBean: RelayBatonBean? = null,
    var brookBean: BrookBean? = null,
    var chainBean: ChainBean? = null,
) : Parcelable {

    companion object {
        const val TYPE_SOCKS = 0
        const val TYPE_HTTP = 1
        const val TYPE_SS = 2
        const val TYPE_SSR = 3
        const val TYPE_VMESS = 4
        const val TYPE_VLESS = 5
        const val TYPE_TROJAN = 6
        const val TYPE_TROJAN_GO = 7
        const val TYPE_NAIVE = 9
        const val TYPE_PING_TUNNEL = 10
        const val TYPE_RELAY_BATON = 11
        const val TYPE_BROOK = 12

        const val TYPE_CHAIN = 8

        val chainName by lazy { app.getString(R.string.proxy_chain) }

        @JvmField
        val CREATOR = object : Parcelable.Creator<ProxyEntity> {
            override fun createFromParcel(parcel: Parcel): ProxyEntity {
                return ProxyEntity(parcel)
            }

            override fun newArray(size: Int): Array<ProxyEntity?> {
                return arrayOfNulls(size)
            }
        }
    }

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
        parcel.readLong()
    ) {
        dirty = parcel.readByte() > 0
        val byteArray = ByteArray(parcel.readInt())
        parcel.readByteArray(byteArray)
        when (type) {
            TYPE_SOCKS -> socksBean = KryoConverters.socksDeserialize(byteArray)
            TYPE_HTTP -> httpBean = KryoConverters.httpDeserialize(byteArray)
            TYPE_SS -> ssBean = KryoConverters.shadowsocksDeserialize(byteArray)
            TYPE_SSR -> ssrBean = KryoConverters.shadowsocksRDeserialize(byteArray)
            TYPE_VMESS -> vmessBean = KryoConverters.vmessDeserialize(byteArray)
            TYPE_VLESS -> vlessBean = KryoConverters.vlessDeserialize(byteArray)
            TYPE_TROJAN -> trojanBean = KryoConverters.trojanDeserialize(byteArray)
            TYPE_TROJAN_GO -> trojanGoBean = KryoConverters.trojanGoDeserialize(byteArray)
            TYPE_NAIVE -> naiveBean = KryoConverters.naiveDeserialize(byteArray)
            TYPE_PING_TUNNEL -> ptBean = KryoConverters.pingTunnelDeserialize(byteArray)
            TYPE_RELAY_BATON -> rbBean = KryoConverters.relayBatonDeserialize(byteArray)
            TYPE_BROOK -> brookBean = KryoConverters.brookDeserialize(byteArray)

            TYPE_CHAIN -> chainBean = KryoConverters.chainDeserialize(byteArray)
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
            TYPE_SOCKS -> "SOCKS5"
            TYPE_HTTP -> if (httpBean!!.tls) "HTTPS" else "HTTP"
            TYPE_SS -> "Shadowsocks"
            TYPE_SSR -> "ShadowsocksR"
            TYPE_VMESS -> "VMess"
            TYPE_VLESS -> "VLESS"
            TYPE_TROJAN -> "Trojan"
            TYPE_TROJAN_GO -> "Trojan-Go"
            TYPE_NAIVE -> "Naïve"
            TYPE_PING_TUNNEL -> "PingTunnel"
            TYPE_RELAY_BATON -> "relaybaton"
            TYPE_BROOK -> "Brook"
            TYPE_CHAIN -> chainName
            else -> "Undefined type $type"
        }
    }

    fun displayName(): String {
        return requireBean().displayName()
    }

    fun urlFixed(): String {
        val bean = requireBean()
        if (bean is ChainBean) {
            if (bean.proxies.isNotEmpty()) {
                val firstEntity = ProfileManager.getProfile(bean.proxies[0])
                if (firstEntity != null) {
                    return firstEntity.urlFixed();
                }
            }
        }
        return if (Validator.isIpv6(bean.serverAddress)) {
            "[${bean.serverAddress}]:${bean.serverPort}"
        } else {
            "${bean.serverAddress}:${bean.serverPort}"
        }
    }

    fun requireBean(): AbstractBean {
        return when (type) {
            TYPE_SOCKS -> socksBean
            TYPE_HTTP -> httpBean
            TYPE_SS -> ssBean
            TYPE_SSR -> ssrBean
            TYPE_VMESS -> vmessBean
            TYPE_VLESS -> vlessBean
            TYPE_TROJAN -> trojanBean
            TYPE_TROJAN_GO -> trojanGoBean
            TYPE_NAIVE -> naiveBean
            TYPE_PING_TUNNEL -> ptBean
            TYPE_RELAY_BATON -> rbBean
            TYPE_BROOK -> brookBean

            TYPE_CHAIN -> chainBean
            else -> error("Undefined type $type")
        } ?: error("Null ${displayType()} profile")
    }

    fun toLink(): String? = with(requireBean()) {
        when (this) {
            is SOCKSBean -> toUri()
            is HttpBean -> toUri()
            is ShadowsocksBean -> toUri()
            is ShadowsocksRBean -> toUri()
            is VMessBean -> toUri()
            is VLESSBean -> toUri()
            is TrojanBean -> toUri()
            is TrojanGoBean -> toUri()
            is NaiveBean -> toUri()
            is PingTunnelBean -> toUri()
            is RelayBatonBean -> toUri()
            is BrookBean -> toUri()
            else -> null
        }
    }

    fun needExternal(): Boolean {
        return when (type) {
            TYPE_SOCKS -> false
            TYPE_HTTP -> false
            TYPE_SS -> useExternalShadowsocks()
            TYPE_SSR -> true
            TYPE_VMESS -> false
            TYPE_VLESS -> useXray()
            TYPE_TROJAN -> useXray()
            TYPE_TROJAN_GO -> true
            TYPE_NAIVE -> true
            TYPE_CHAIN -> false
            TYPE_PING_TUNNEL -> true
            TYPE_RELAY_BATON -> true
            TYPE_BROOK -> true
            else -> error("Undefined type $type")
        }
    }

    fun isV2RayNetworkTcp(): Boolean {
        val bean = requireBean() as StandardV2RayBean
        return when (bean.type) {
            "tcp", "ws", "http" -> true
            else -> false
        }
    }

    fun needCoreMux(): Boolean {
        val enableMuxForAll by lazy { DataStore.enableMuxForAll }
        return when (type) {
            TYPE_VMESS -> isV2RayNetworkTcp()
            TYPE_VLESS -> !useXray()
            TYPE_TROJAN -> enableMuxForAll && !useXray()
            TYPE_TROJAN_GO -> false
            else -> enableMuxForAll
        }
    }

    fun useExternalShadowsocks(): Boolean {
        val bean = ssBean ?: return false
        if (DataStore.forceShadowsocksRust) return true
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
                if (bean.security != "xtls") return false
                if (bean.type != "tcp") return false
                if (bean.headerType.isNotBlank() && bean.headerType != "none") return false
                return true
            }
            is TrojanBean -> {
                if (bean.security == "xtls") return true
            }
        }

        return false
    }

    fun putBean(bean: AbstractBean) {
        socksBean = null
        httpBean = null
        ssBean = null
        ssrBean = null
        vmessBean = null
        vlessBean = null
        trojanBean = null
        trojanGoBean = null
        naiveBean = null
        ptBean = null
        rbBean = null
        brookBean = null
        chainBean = null
        when (bean) {
            is SOCKSBean -> {
                type = TYPE_SOCKS
                socksBean = bean
            }
            is HttpBean -> {
                type = TYPE_HTTP
                httpBean = bean
            }
            is ShadowsocksBean -> {
                type = TYPE_SS
                ssBean = bean
            }
            is ShadowsocksRBean -> {
                type = TYPE_SSR
                ssrBean = bean
            }
            is VMessBean -> {
                type = TYPE_VMESS
                vmessBean = bean
            }
            is VLESSBean -> {
                type = TYPE_VLESS
                vlessBean = bean
            }
            is TrojanBean -> {
                type = TYPE_TROJAN
                trojanBean = bean
            }
            is TrojanGoBean -> {
                type = TYPE_TROJAN_GO
                trojanGoBean = bean
            }
            is NaiveBean -> {
                type = TYPE_NAIVE
                naiveBean = bean
            }
            is PingTunnelBean -> {
                type = TYPE_PING_TUNNEL
                ptBean = bean
            }
            is RelayBatonBean -> {
                type = TYPE_RELAY_BATON
                rbBean = bean
            }
            is BrookBean -> {
                type = TYPE_BROOK
                brookBean = bean
            }
            is ChainBean -> {
                type = TYPE_CHAIN
                chainBean = bean
            }
            else -> error("Undefined type $type")
        }
    }

    fun settingIntent(ctx: Context, isSubscription: Boolean): Intent {
        return Intent(
            ctx, when (type) {
                TYPE_SOCKS -> SocksSettingsActivity::class.java
                TYPE_HTTP -> HttpSettingsActivity::class.java
                TYPE_SS -> ShadowsocksSettingsActivity::class.java
                TYPE_SSR -> ShadowsocksRSettingsActivity::class.java
                TYPE_VMESS -> VMessSettingsActivity::class.java
                TYPE_VLESS -> VLESSSettingsActivity::class.java
                TYPE_TROJAN -> TrojanSettingsActivity::class.java
                TYPE_TROJAN_GO -> TrojanGoSettingsActivity::class.java
                TYPE_NAIVE -> NaiveSettingsActivity::class.java
                TYPE_PING_TUNNEL -> PingTunnelSettingsActivity::class.java
                TYPE_RELAY_BATON -> RelayBatonSettingsActivity::class.java
                TYPE_BROOK -> BrookSettingsActivity::class.java
                TYPE_CHAIN -> ChainSettingsActivity::class.java
                else -> throw IllegalArgumentException()
            }
        ).apply {
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

        @Query("SELECT * FROM proxy_entities WHERE id in (:proxyIds)")
        fun getEntities(proxyIds: List<Long>): List<ProxyEntity>

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
        fun deleteProxy(vararg proxy: ProxyEntity): Int

        @Update
        fun updateProxy(vararg proxy: ProxyEntity): Int

        @Insert
        fun addProxy(proxy: ProxyEntity): Long

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        fun deleteAll(groupId: Long): Int

    }

    override fun describeContents(): Int {
        return 0
    }

}
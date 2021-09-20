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

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginManager
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ShadowsocksProvider
import io.nekohasekai.sagernet.ShadowsocksStreamProvider
import io.nekohasekai.sagernet.TrojanProvider
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.brook.BrookBean
import io.nekohasekai.sagernet.fmt.buildV2RayConfig
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.http.toUri
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.buildHysteriaConfig
import io.nekohasekai.sagernet.fmt.internal.BalancerBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.internal.ConfigBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildNaiveConfig
import io.nekohasekai.sagernet.fmt.naive.toUri
import io.nekohasekai.sagernet.fmt.pingtunnel.PingTunnelBean
import io.nekohasekai.sagernet.fmt.pingtunnel.toUri
import io.nekohasekai.sagernet.fmt.relaybaton.RelayBatonBean
import io.nekohasekai.sagernet.fmt.relaybaton.buildRelayBatonConfig
import io.nekohasekai.sagernet.fmt.shadowsocks.*
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean
import io.nekohasekai.sagernet.fmt.shadowsocksr.buildShadowsocksRConfig
import io.nekohasekai.sagernet.fmt.shadowsocksr.toUri
import io.nekohasekai.sagernet.fmt.snell.SnellBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.toUri
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan.toUri
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.trojan_go.buildTrojanGoConfig
import io.nekohasekai.sagernet.fmt.trojan_go.toUri
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.toUri
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.ssSecureList
import io.nekohasekai.sagernet.ui.profile.*

@Entity(
    tableName = "proxy_entities", indices = [Index("groupId", name = "groupId")]
)
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var groupId: Long = 0L,
    var type: Int = 0,
    var userOrder: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
    var status: Int = 0,
    var ping: Int = 0,
    var uuid: String = "",
    var error: String? = null,
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
    var hysteriaBean: HysteriaBean? = null,
    var snellBean: SnellBean? = null,
    var sshBean: SSHBean? = null,
    var wgBean: WireGuardBean? = null,
    var configBean: ConfigBean? = null,
    var chainBean: ChainBean? = null,
    var balancerBean: BalancerBean? = null
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
        const val TYPE_HYSTERIA = 15
        const val TYPE_SNELL = 16
        const val TYPE_SSH = 17
        const val TYPE_WG = 18

        const val TYPE_CHAIN = 8
        const val TYPE_BALANCER = 14
        const val TYPE_CONFIG = 13

        val chainName by lazy { app.getString(R.string.proxy_chain) }
        val configName by lazy { app.getString(R.string.custom_config) }
        val balancerName by lazy { app.getString(R.string.balancer) }

        private val placeHolderBean = SOCKSBean().applyDefaultValues()

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
        putByteArray(byteArray)
    }

    fun putByteArray(byteArray: ByteArray) {
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
            TYPE_HYSTERIA -> hysteriaBean = KryoConverters.hysteriaDeserialize(byteArray)
            TYPE_SNELL -> snellBean = KryoConverters.snellDeserialize(byteArray)
            TYPE_SSH -> sshBean = KryoConverters.sshDeserialize(byteArray)
            TYPE_WG -> wgBean = KryoConverters.wireguardDeserialize(byteArray)

            TYPE_CONFIG -> configBean = KryoConverters.configDeserialize(byteArray)
            TYPE_CHAIN -> chainBean = KryoConverters.chainDeserialize(byteArray)
            TYPE_BALANCER -> balancerBean = KryoConverters.balancerBeanDeserialize(byteArray)
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

    fun displayType() = when (type) {
        TYPE_SOCKS -> socksBean!!.protocolName()
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
        TYPE_HYSTERIA -> "Hysteria"
        TYPE_SNELL -> "Snell"
        TYPE_SSH -> "SSH"
        TYPE_WG -> "WireGuard"
        TYPE_CHAIN -> chainName
        TYPE_CONFIG -> configName
        TYPE_BALANCER -> balancerName
        else -> "Undefined type $type"
    }

    fun displayName() = requireBean().displayName()
    fun displayAddress() = requireBean().displayAddress()

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
            TYPE_HYSTERIA -> hysteriaBean
            TYPE_SNELL -> snellBean
            TYPE_SSH -> sshBean
            TYPE_WG -> wgBean

            TYPE_CONFIG -> configBean
            TYPE_CHAIN -> chainBean
            TYPE_BALANCER -> balancerBean
            else -> error("Undefined type $type")
        } ?: error("Null ${displayType()} profile")
    }

    fun haveLink(): Boolean {
        return when (type) {
            TYPE_CHAIN -> false
            TYPE_CONFIG -> false
            TYPE_BALANCER -> false
            else -> true
        }
    }

    fun haveStandardLink(): Boolean {
        return when (requireBean()) {
            is RelayBatonBean -> false
            is BrookBean -> false
            is ConfigBean -> false
            is HysteriaBean -> false
            is SnellBean -> false
            is SSHBean -> false
            is WireGuardBean -> false
            else -> true
        }
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
            is RelayBatonBean -> toUniversalLink()
            is BrookBean -> toUniversalLink()
            is ConfigBean -> toUniversalLink()
            is HysteriaBean -> toUniversalLink()
            is SnellBean -> toUniversalLink()
            is SSHBean -> toUniversalLink()
            is WireGuardBean -> toUniversalLink()
            else -> null
        }
    }

    fun exportConfig(): Pair<String, String> {
        var name = "profile.json"

        return with(requireBean()) {
            StringBuilder().apply {
                val config = buildV2RayConfig(this@ProxyEntity)
                append(config.config)

                if (!config.index.all { it.chain.isEmpty() }) {
                    name = "profiles.txt"
                }

                for ((isBalancer, chain) in config.index) {
                    chain.entries.forEachIndexed { index, (port, profile) ->
                        val needChain = !isBalancer && index != chain.size - 1
                        val needMux = index == 0 && DataStore.enableMux
                        when (val bean = profile.requireBean()) {
                            is ShadowsocksBean -> {
                                append("\n\n")
                                append(bean.buildShadowsocksConfig(port))

                            }
                            is ShadowsocksRBean -> {
                                append("\n\n")
                                append(bean.buildShadowsocksRConfig())
                            }
                            is TrojanGoBean -> {
                                append("\n\n")
                                append(bean.buildTrojanGoConfig(port, needMux))
                            }
                            is NaiveBean -> {
                                append("\n\n")
                                append(bean.buildNaiveConfig(port, needMux))
                            }
                            is RelayBatonBean -> {
                                append("\n\n")
                                append(bean.buildRelayBatonConfig(port))
                            }
                            is HysteriaBean -> {
                                append("\n\n")
                                append(bean.buildHysteriaConfig(port, null))
                            }
                        }
                    }
                }
            }.toString()
        } to name
    }

    fun needExternal(): Boolean {
        return when (type) {
            TYPE_SOCKS -> false
            TYPE_HTTP -> false
            TYPE_SS -> pickShadowsocksProvider() != ShadowsocksProvider.V2RAY
            TYPE_VMESS -> false
            TYPE_VLESS -> false
            TYPE_TROJAN -> DataStore.providerTrojan != TrojanProvider.V2RAY
            TYPE_CHAIN -> false
            TYPE_BALANCER -> false
            else -> true
        }
    }

    fun useClashBased(): Boolean {
        if (!needExternal()) return false
        return when (type) {
            TYPE_SS -> pickShadowsocksProvider() == ShadowsocksProvider.CLASH
            TYPE_SSR -> true
            TYPE_SNELL -> true
            TYPE_SSH -> true
            else -> false
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
            TYPE_VMESS, TYPE_VLESS -> isV2RayNetworkTcp()
            TYPE_TROJAN_GO -> false
            else -> enableMuxForAll
        }
    }

    fun pickShadowsocksProvider(): Int {
        val bean = ssBean ?: return -1
        if (bean.method.contains(ssSecureList)) {
            val prefer = DataStore.providerShadowsocksAEAD
            when {
                prefer == ShadowsocksProvider.V2RAY && bean.method in methodsV2fly && bean.plugin.isBlank() -> {
                    return ShadowsocksProvider.V2RAY
                }
                prefer == ShadowsocksProvider.CLASH && bean.method in methodsClash && ssPluginSupportedByClash(
                    true
                ) -> {
                    return ShadowsocksProvider.CLASH
                }
                prefer == ShadowsocksProvider.SHADOWSOCKS_RUST && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bean.method in methodsSsRust && !ssPluginSupportedByClash(
                    false
                ) -> {
                    return ShadowsocksProvider.SHADOWSOCKS_RUST
                }
                prefer == ShadowsocksProvider.SHADOWSOCKS_LIBEV && bean.method in methodsSsLibev && !ssPluginSupportedByClash(
                    false
                ) -> {
                    return ShadowsocksProvider.SHADOWSOCKS_LIBEV
                }
            }
            return if (ssPreferClash()) {
                ShadowsocksProvider.CLASH
            } else if (bean.method in methodsV2fly && bean.plugin.isBlank()) {
                ShadowsocksProvider.V2RAY
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ShadowsocksProvider.SHADOWSOCKS_RUST
            } else {
                ShadowsocksProvider.SHADOWSOCKS_LIBEV
            }
        } else {
            val prefer = DataStore.providerShadowsocksStream
            when {
                prefer == ShadowsocksStreamProvider.CLASH && bean.method in methodsClash && ssPluginSupportedByClash(
                    true
                ) -> {
                    return ShadowsocksProvider.CLASH
                }
                prefer == ShadowsocksStreamProvider.SHADOWSOCKS_RUST && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bean.method in methodsSsRust && !ssPluginSupportedByClash(
                    false
                ) -> {
                    return ShadowsocksProvider.SHADOWSOCKS_RUST
                }
                prefer == ShadowsocksStreamProvider.SHADOWSOCKS_LIBEV && bean.method in methodsSsLibev && !ssPluginSupportedByClash(
                    false
                ) -> {
                    return ShadowsocksProvider.SHADOWSOCKS_LIBEV
                }
            }
            return if (ssPreferClash()) {
                ShadowsocksProvider.CLASH
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ShadowsocksProvider.SHADOWSOCKS_RUST
            } else {
                ShadowsocksProvider.SHADOWSOCKS_LIBEV
            }
        }
    }

    fun ssPluginSupportedByClash(prefer: Boolean): Boolean {
        val bean = ssBean ?: return false
        if (bean.plugin.isNotBlank()) {
            val plugin = PluginConfiguration(bean.plugin)
            if (plugin.selected !in arrayOf("obfs-local", "v2ray-plugin")) return false
            if (plugin.selected == "v2ray-plugin") {
                if (plugin.getOptions()["mode"] != "websocket") return false
            }
            try {
                PluginManager.init(plugin)
                return prefer
            } catch (e: Exception) {
            }
            return true
        }
        return prefer
    }

    fun ssPreferClash(): Boolean {
        val bean = ssBean ?: return false
        val onlyClash = bean.method !in methodsV2fly && bean.method !in methodsSsRust && bean.method !in methodsSsLibev
        return onlyClash || ssPluginSupportedByClash(false)
    }

    fun putBean(bean: AbstractBean): ProxyEntity {
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
        hysteriaBean = null
        snellBean = null
        sshBean = null
        wgBean = null

        configBean = null
        chainBean = null
        balancerBean = null

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
            is HysteriaBean -> {
                type = TYPE_HYSTERIA
                hysteriaBean = bean
            }
            is SnellBean -> {
                type = TYPE_SNELL
                snellBean = bean
            }
            is SSHBean -> {
                type = TYPE_SSH
                sshBean = bean
            }
            is WireGuardBean -> {
                type = TYPE_WG
                wgBean = bean
            }
            is ConfigBean -> {
                type = TYPE_CONFIG
                configBean = bean
            }
            is ChainBean -> {
                type = TYPE_CHAIN
                chainBean = bean
            }
            is BalancerBean -> {
                type = TYPE_BALANCER
                balancerBean = bean
            }
            else -> error("Undefined type $type")
        }
        return this
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
                TYPE_HYSTERIA -> HysteriaSettingsActivity::class.java
                TYPE_SNELL -> SnellSettingsActivity::class.java
                TYPE_SSH -> SSHSettingsActivity::class.java
                TYPE_WG -> WireGuardSettingsActivity::class.java

                TYPE_CONFIG -> ConfigSettingsActivity::class.java
                TYPE_CHAIN -> ChainSettingsActivity::class.java
                TYPE_BALANCER -> BalancerSettingsActivity::class.java
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

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        fun deleteByGroup(groupId: Long)

        @Query("DELETE FROM proxy_entities WHERE groupId in (:groupId)")
        fun deleteByGroup(groupId: LongArray)

        @Delete
        fun deleteProxy(proxy: ProxyEntity): Int

        @Delete
        fun deleteProxy(proxies: List<ProxyEntity>): Int

        @Update
        fun updateProxy(proxy: ProxyEntity): Int

        @Update
        fun updateProxy(proxies: List<ProxyEntity>): Int

        @Insert
        fun addProxy(proxy: ProxyEntity): Long

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        fun deleteAll(groupId: Long): Int

    }

    override fun describeContents(): Int {
        return 0
    }

}
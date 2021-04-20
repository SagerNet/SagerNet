package io.nekohasekai.sagernet.database

import android.content.Context
import android.content.Intent
import androidx.room.*
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ui.settings.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.settings.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.settings.SocksSettingsActivity

@Entity(tableName = "proxy_entities", indices = [
    Index("groupId", name = "groupId")
])
class ProxyEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    var groupId: Long,
    var type: String = "",
    var userOrder: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
    var proxyApps: Int = 0,
    var individual: String? = null,
    var meteredNetwork: Int = 0,
    var vmessBean: VMessBean? = null,
    var socksBean: SOCKSBean? = null,
    var ssBean: ShadowsocksBean? = null,
) {

    fun displayType(): String {
        return when (type) {
            "vmess" -> "VMess"
            "socks" -> "SOCKS5"
            "ss" -> "Shadowsocks"
            else -> "Undefined type $type"
        }
    }

    fun displayName(): String {
        return requireBean().name.takeIf { !it.isNullOrBlank() }
            ?: "${requireBean().serverAddress}:${requireBean().serverPort}"
    }

    fun requireBean(): AbstractBean {
        return when (type) {
            "vmess" -> vmessBean ?: error("Null vmess node")
            "socks" -> socksBean ?: error("Null socks node")
            "ss" -> ssBean ?: error("Null ss node")
            else -> error("Undefined type $type")
        }
    }

    fun putBean(bean: AbstractBean) {
        when (bean) {
            is SOCKSBean -> {
                type = "socks"
                socksBean = bean
            }
            is VMessBean -> {
                type = "vmess"
                vmessBean = bean
            }
            is ShadowsocksBean -> {
                type = "ss"
                ssBean = bean
            }
            else -> error("Undefined type $type")
        }
    }

    fun requireVMess() = requireBean() as VMessBean
    fun requireSOCKS() = requireBean() as SOCKSBean
    fun requireSS() = requireBean() as ShadowsocksBean

    fun settingIntent(ctx: Context): Intent {
        return Intent(ctx, when (type) {
            "socks" -> SocksSettingsActivity::class.java
            "ss" -> ShadowsocksSettingsActivity::class.java
            else -> throw IllegalArgumentException()
        }).apply {
            putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, id)
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getByGroup(groupId: Long): List<ProxyEntity>

        @Query("SELECT COUNT(*) FROM proxy_entities WHERE groupId = :groupId")
        fun countByGroup(groupId: Long): Long

        @Query("SELECT  MAX(userOrder) + 1 FROM proxy_entities WHERE groupId = :groupId")
        fun nextOrder(groupId: Long): Long?

        @Query("SELECT * FROM proxy_entities WHERE id = :proxyId")
        fun getById(proxyId: Long): ProxyEntity?

        @Query("DELETE FROM proxy_entities WHERE id = :proxyId")
        fun deleteById(proxyId: Long): Int

        @Insert
        fun addProxy(proxy: ProxyEntity): Long

        @Update
        fun updateProxy(vararg proxy: ProxyEntity)

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        fun deleteAll(groupId: Long): Int

    }
}
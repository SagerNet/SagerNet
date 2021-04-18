package io.nekohasekai.sagernet.database

import androidx.room.*
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean

@Entity(tableName = "proxy_entities", indices = [
    Index("groupId", name = "groupId")
])
class ProxyEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    var groupId: Long,
    var type: String,
    var userOrder: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
    var proxyApps: Int = 0,
    var individual: String? = null,
    var meteredNetwork: Int = 0,
    var vmessBean: VMessBean? = null,
    var socksBean: SOCKSBean? = null,
) {

    fun displayType(): String {
        return when (type) {
            "vmess" -> "VMess"
            "socks" -> "SOCKS5"
            else -> "Undefined type $type"
        }
    }

    fun displayName(): String {
        return requireBean().name
    }

    fun requireBean(): AbstractBean {
        return when (type) {
            "vmess" -> vmessBean ?: error("Null vmess node")
            "socks" -> socksBean ?: error("Null socks node")
            else -> error("Undefined type $type")
        }
    }

    fun requireVMess() = requireBean() as VMessBean
    fun requireSOCKS() = requireBean() as SOCKSBean

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getByGroup(groupId: Long): List<ProxyEntity>

        @Query("SELECT * FROM proxy_entities WHERE id = :proxyId")
        fun getById(proxyId: Long): ProxyEntity?

        @Insert
        fun addProxy(proxy: ProxyEntity)

        @Update
        fun updateProxy(proxy: ProxyEntity)

    }
}
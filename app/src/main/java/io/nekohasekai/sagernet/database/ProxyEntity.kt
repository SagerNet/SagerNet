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
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readInt(),
        parcel.readString(),
        parcel.readInt()) {
        dirty = parcel.readByte() > 0
        val byteArray = ByteArray(parcel.readInt())
        parcel.readByteArray(byteArray)
        when (type) {
            "socks" -> socksBean = KryoConverters.socksDeserialize(byteArray)
            "ss" -> ssBean = KryoConverters.shadowsocksDeserialize(byteArray)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(groupId)
        parcel.writeString(type)
        parcel.writeLong(userOrder)
        parcel.writeLong(tx)
        parcel.writeLong(rx)
        parcel.writeInt(proxyApps)
        parcel.writeString(individual)
        parcel.writeInt(meteredNetwork)
        parcel.writeByte(if (dirty) 1 else 0)
        val byteArray = KryoConverters.serialize(requireBean())
        parcel.writeInt(byteArray.size)
        parcel.writeByteArray(byteArray)
    }

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

    fun useExternalShadowsocks(): Boolean {
        if (type != "ss") return false
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

        @Query("DELETE FROM proxy_entities WHERE id = :proxyId")
        fun deleteById(proxyId: Long): Int

        @Insert
        fun addProxy(proxy: ProxyEntity): Long

        @Update
        fun updateProxy(vararg proxy: ProxyEntity)

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
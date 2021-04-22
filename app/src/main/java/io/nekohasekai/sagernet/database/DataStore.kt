package io.nekohasekai.sagernet.database

import android.os.Binder
import android.os.Build
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.RouteMode
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.ktx.boolean
import io.nekohasekai.sagernet.ktx.long
import io.nekohasekai.sagernet.ktx.parsePort
import io.nekohasekai.sagernet.ktx.string
import io.nekohasekai.sagernet.utils.DirectBoot

object DataStore : OnPreferenceDataStoreChangeListener {

    val configurationStore = RoomPreferenceDataStore(PublicDatabase.kvPairDao)
    val profileCacheStore = RoomPreferenceDataStore(SagerDatabase.profileCacheDao)

    fun init() {
        if (Build.VERSION.SDK_INT >= 24) {
            SagerNet.deviceStorage.moveDatabaseFrom(SagerNet.application, Key.DB_PUBLIC)
        }
        if (Build.VERSION.SDK_INT >= 24 && directBootAware && SagerNet.user.isUserUnlocked) {
            DirectBoot.flushTrafficStats()
        }
    }

    var selectedProxy by configurationStore.long(Key.PROFILE_ID)
    var serviceMode by configurationStore.string(Key.SERVICE_MODE) { Key.MODE_VPN }
    var routeMode by configurationStore.string(Key.ROUTE_MODE) { RouteMode.ALL }
    var allowAccess by configurationStore.boolean(Key.ALLOW_ACCESS)

    var enableLocalDNS by configurationStore.boolean(Key.ENABLE_LOCAL_DNS)
    var remoteDNS by configurationStore.string(Key.REMOTE_DNS) { "1.1.1.1" }
    var domesticDns by configurationStore.string(Key.DOMESTIC_DNS) { "9.9.9.11" }

    // hopefully hashCode = mHandle doesn't change, currently this is true from KitKat to Nougat
    private val userIndex by lazy { Binder.getCallingUserHandle().hashCode() }
    var socksPort: Int
        get() = getLocalPort(Key.SOCKS_PORT, 2080)
        set(value) = saveLocalPort(Key.SOCKS_PORT, value)
    var localDNSPort: Int
        get() = getLocalPort(Key.LOCAL_DNS_PORT, 6450)
        set(value) {
            saveLocalPort(Key.LOCAL_DNS_PORT, value)
        }

    fun initGlobal() {
        persistAcrossReboot
        if (configurationStore.getString(Key.SOCKS_PORT) == null) socksPort = socksPort
        if (configurationStore.getString(Key.LOCAL_DNS_PORT) == null) localDNSPort = localDNSPort
    }


    private fun getLocalPort(key: String, default: Int): Int {
        return parsePort(configurationStore.getString(key), default + userIndex)
    }

    private fun saveLocalPort(key: String, value: Int) {
        configurationStore.putString(key, "$value")
    }

    var ipv6Route by configurationStore.boolean(Key.IPV6_ROUTE)
    var preferIpv6 by configurationStore.boolean(Key.PREFER_IPV6)
    var meteredNetwork by configurationStore.boolean(Key.METERED_NETWORK)
    var proxyApps by configurationStore.boolean(Key.PROXY_APPS)
    var bypass by configurationStore.boolean(Key.BYPASS_MODE)
    var individual by configurationStore.string("individual")
    var forceShadowsocksRust by configurationStore.boolean("forceShadowsocksRust")

    val persistAcrossReboot by configurationStore.boolean(Key.PERSIST_ACROSS_REBOOT) { true }
    val canToggleLocked: Boolean get() = configurationStore.getBoolean(Key.DIRECT_BOOT_AWARE) == true
    val directBootAware: Boolean get() = SagerNet.directBootSupported && canToggleLocked


    // cache
    var dirty by profileCacheStore.boolean(Key.PROFILE_DIRTY)
    var editingId by profileCacheStore.long(Key.PROFILE_ID)
    var editingGroup by profileCacheStore.long(Key.PROFILE_GROUP)
    var profileName by profileCacheStore.string(Key.PROFILE_NAME)
    var serverAddress by profileCacheStore.string(Key.SERVER_ADDRESS)
    var serverPort by profileCacheStore.string(Key.SERVER_PORT)
    var serverUsername by profileCacheStore.string(Key.SERVER_USERNAME)
    var serverPassword by profileCacheStore.string(Key.SERVER_PASSWORD)
    var serverUdp by profileCacheStore.boolean(Key.SERVER_UDP)
    var serverMethod by profileCacheStore.string(Key.SERVER_METHOD)
    var serverPlugin by profileCacheStore.string(Key.SERVER_PLUGIN)
    var serverPluginConfigure by profileCacheStore.string(Key.SERVER_PLUGIN_CONFIGURE)

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.PROFILE_ID -> if (directBootAware) DirectBoot.update()
        }
    }
}
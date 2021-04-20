package io.nekohasekai.sagernet.database

import android.os.Build
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.ktx.boolean
import io.nekohasekai.sagernet.ktx.int
import io.nekohasekai.sagernet.ktx.long
import io.nekohasekai.sagernet.ktx.string
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON

object DataStore {

    val configurationStore = RoomPreferenceDataStore(PublicDatabase.kvPairDao)
    val profileCacheStore = RoomPreferenceDataStore(SagerDatabase.profileCacheDao)

    fun init() {
        if (Build.VERSION.SDK_INT >= 24) {
            SagerNet.deviceStorage.moveDatabaseFrom(SagerNet.application, Key.DB_PUBLIC)
        }

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)

    }

    var serviceMode by configurationStore.int(Key.SERVICE_MODE)
    var selectedProxy by configurationStore.long("selected_proxy")
    var allowAccess by configurationStore.boolean("allow_access")
    var socks5Port by configurationStore.int("socks5_port") { 3389 }
    var useHttp by configurationStore.boolean("use_http")
    var httpPort by configurationStore.long("http_port")
    var ipv6Route by configurationStore.boolean("ipv6_route")
    var preferIpv6 by configurationStore.boolean("prefer_ipv6")
    var meteredNetwork by configurationStore.boolean("metered_network")
    var proxyApps by configurationStore.int("proxyApps")
    var individual by configurationStore.string("individual")
    var forceShadowsocksRust = true//by configurationStore.boolean("forceShadowsocksRust")

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

}
package io.nekohasekai.sagernet.database

import android.os.Build
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerApp
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.ktx.boolean
import io.nekohasekai.sagernet.ktx.int
import io.nekohasekai.sagernet.ktx.long
import io.nekohasekai.sagernet.ktx.string
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON

object DataStore {

    val publicStore = RoomPreferenceDataStore(PublicDatabase.kvPairDao)
    val sagerStore = RoomPreferenceDataStore(SagerDatabase.kvPairDao)

    fun init() {
        if (Build.VERSION.SDK_INT >= 24) {
            SagerApp.deviceStorage.moveDatabaseFrom(SagerApp.application, Key.DB_PUBLIC)
        }

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)

    }

    var serviceMode by sagerStore.int(Key.SERVICE_MODE)
    var selectedProxy by sagerStore.long("selected_proxy")
    var allowAccess by sagerStore.boolean("allow_access")
    var socks5Port by sagerStore.int("socks5_port") { 3389 }
    var useHttp by sagerStore.boolean("use_http")
    var httpPort by sagerStore.long("http_port")
    var ipv6Route by sagerStore.boolean("ipv6_route")
    var preferIpv6 by sagerStore.boolean("prefer_ipv6")
    var meteredNetwork by sagerStore.boolean("metered_network")
    var proxyApps by sagerStore.int("proxyApps")
    var individual by sagerStore.string("individual")

}
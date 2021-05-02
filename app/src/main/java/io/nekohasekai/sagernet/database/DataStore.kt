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

import android.os.Binder
import android.os.Build
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.DirectBoot
import kotlin.properties.Delegates

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
    var selectedGroup by configurationStore.long(Key.PROFILE_GROUP) {
        SagerNet.currentProfile?.groupId ?: 0L
    }

    suspend fun selectedGroupForImport(): Long {
        val groups = SagerDatabase.groupDao.allGroups()
        val selectedGroup = SagerDatabase.groupDao.getById(selectedGroup) ?: groups[0]
        var targetIndex by Delegates.notNull<Int>()
        val targetId = if (!selectedGroup.isSubscription) {
            selectedGroup.id
        } else {
            targetIndex = groups.indexOfFirst { !it.isSubscription }
            groups[targetIndex].id
        }
        return targetId
    }

    var serviceMode by configurationStore.string(Key.SERVICE_MODE) { Key.MODE_VPN }

    var domainStrategy by configurationStore.string(Key.PROFILE_NAME) { "AsIs" }
    var domainMatcher by configurationStore.string(Key.DOMAIN_MATCHER) { "mph" }
    var trafficSniffing by configurationStore.boolean(Key.TRAFFIC_SNIFFING) { true }

    var bypassLan by configurationStore.boolean(Key.BYPASS_LAN) { true }
    var routeChina by configurationStore.stringToInt(Key.ROUTE_CHINA)
    var blockAds by configurationStore.boolean(Key.BLOCK_ADS) { false }

    var allowAccess by configurationStore.boolean(Key.ALLOW_ACCESS)
    var speedInterval by configurationStore.stringToInt(Key.SPEED_INTERVAL)

    var enableLocalDNS by configurationStore.boolean(Key.ENABLE_LOCAL_DNS) { true }
    var remoteDNS by configurationStore.string(Key.REMOTE_DNS) { "https://1.1.1.1/dns-query" }
    var domesticDns by configurationStore.string(Key.DOMESTIC_DNS) { "9.9.9.11" }
    var securityAdvisory by configurationStore.boolean(Key.SECURITY_ADVISORY)

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
    var httpPort: Int
        get() = getLocalPort(Key.HTTP_PORT, 9080)
        set(value) = saveLocalPort(Key.HTTP_PORT, value)

    fun initGlobal() {
        if (configurationStore.getString(Key.SOCKS_PORT) == null) socksPort = socksPort
        if (configurationStore.getString(Key.LOCAL_DNS_PORT) == null) localDNSPort = localDNSPort
        if (configurationStore.getString(Key.HTTP_PORT) == null) httpPort = httpPort
    }


    private fun getLocalPort(key: String, default: Int): Int {
        return parsePort(configurationStore.getString(key), default + userIndex)
    }

    private fun saveLocalPort(key: String, value: Int) {
        configurationStore.putString(key, "$value")
    }

    var ipv6Route by configurationStore.boolean(Key.IPV6_ROUTE) { true }
    var preferIpv6 by configurationStore.boolean(Key.PREFER_IPV6)
    var meteredNetwork by configurationStore.boolean(Key.METERED_NETWORK)
    var proxyApps by configurationStore.boolean(Key.PROXY_APPS)
    var bypass by configurationStore.boolean(Key.BYPASS_MODE)
    var individual by configurationStore.string("individual")
    var forceShadowsocksRust by configurationStore.boolean(Key.FORCE_SHADOWSOCKS_RUST)
    var requireHttp by configurationStore.boolean(Key.REQUIRE_HTTP)
    var enableMux by configurationStore.boolean(Key.ENABLE_MUX)
    var muxConcurrency by configurationStore.stringToInt(Key.MUX_CONCURRENCY) { 8 }
    var showStopButton by configurationStore.boolean(Key.SHOW_STOP_BUTTON)
    var showDirectSpeed by configurationStore.boolean(Key.SHOW_DIRECT_SPEED)

    val persistAcrossReboot by configurationStore.boolean(Key.PERSIST_ACROSS_REBOOT) { true }
    val canToggleLocked: Boolean get() = configurationStore.getBoolean(Key.DIRECT_BOOT_AWARE) == true
    val directBootAware: Boolean get() = SagerNet.directBootSupported && canToggleLocked

    // cache

    var dirty by profileCacheStore.boolean(Key.PROFILE_DIRTY)
    var editingId by profileCacheStore.long(Key.PROFILE_ID)
    var editingGroup by profileCacheStore.long(Key.PROFILE_GROUP)
    var profileName by profileCacheStore.string(Key.PROFILE_NAME)
    var serverAddress by profileCacheStore.string(Key.SERVER_ADDRESS)
    var serverPort by profileCacheStore.stringToInt(Key.SERVER_PORT)
    var serverUsername by profileCacheStore.string(Key.SERVER_USERNAME)
    var serverPassword by profileCacheStore.string(Key.SERVER_PASSWORD)
    var serverPassword1 by profileCacheStore.string(Key.SERVER_PASSWORD1)
    var serverUdp by profileCacheStore.boolean(Key.SERVER_UDP)
    var serverMethod by profileCacheStore.string(Key.SERVER_METHOD)
    var serverPlugin by profileCacheStore.string(Key.SERVER_PLUGIN)

    var serverProtocol by profileCacheStore.string(Key.SERVER_PROTOCOL)
    var serverProtocolParam by profileCacheStore.string(Key.SERVER_PROTOCOL_PARAM)
    var serverObfs by profileCacheStore.string(Key.SERVER_OBFS)
    var serverObfsParam by profileCacheStore.string(Key.SERVER_OBFS_PARAM)

    var serverUserId by profileCacheStore.string(Key.SERVER_USER_ID)
    var serverAlterId by profileCacheStore.stringToInt(Key.SERVER_ALTER_ID)
    var serverSecurity by profileCacheStore.string(Key.SERVER_SECURITY)
    var serverNetwork by profileCacheStore.string(Key.SERVER_NETWORK)
    var serverHeader by profileCacheStore.string(Key.SERVER_HEADER)
    var serverHost by profileCacheStore.string(Key.SERVER_HOST)
    var serverPath by profileCacheStore.string(Key.SERVER_PATH)
    var serverSNI by profileCacheStore.string(Key.SERVER_SNI)
    var serverTLS by profileCacheStore.boolean(Key.SERVER_TLS)
    var serverEncryption by profileCacheStore.string(Key.SERVER_ENCRYPTION)
    var serverALPN by profileCacheStore.string(Key.SERVER_ALPN)
    var serverFlow by profileCacheStore.string(Key.SERVER_FLOW)
    var serverQuicSecurity by profileCacheStore.string(Key.SERVER_QUIC_SECURITY)
    var serverWsMaxEarlyData by profileCacheStore.stringToInt(Key.SERVER_WS_MAX_EARLY_DATA)
    var serverWsBrowserForwarding by profileCacheStore.boolean(Key.SERVER_WS_BROWSER_FORWARDING)

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.PROFILE_ID -> if (directBootAware) DirectBoot.update()
        }
    }
}
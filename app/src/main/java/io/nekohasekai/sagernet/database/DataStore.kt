/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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
import io.nekohasekai.sagernet.*
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.preference.InMemoryDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.DirectBoot

object DataStore : OnPreferenceDataStoreChangeListener {

    val configurationStore = RoomPreferenceDataStore(PublicDatabase.kvPairDao)
    val profileCacheStore = RoomPreferenceDataStore(InMemoryDatabase.kvPairDao)

    fun init() {
        /*if (Build.VERSION.SDK_INT >= 24) {
            SagerNet.deviceStorage.moveDatabaseFrom(SagerNet.application, Key.DB_PUBLIC)
        }
        if (Build.VERSION.SDK_INT >= 24 && directBootAware && SagerNet.user.isUserUnlocked) {
            DirectBoot.flushTrafficStats()
        }*/
    }

    var selectedProxy by configurationStore.long(Key.PROFILE_ID)
    var currentProfile by configurationStore.long(Key.PROFILE_CURRENT)
    var startedProfile by configurationStore.long(Key.PROFILE_STARTED)

    var selectedGroup by configurationStore.long(Key.PROFILE_GROUP) {
        SagerNet.currentProfile?.groupId ?: 0L
    }
    
    fun currentGroupId(): Long = currentGroup().id

    fun currentGroup(): ProxyGroup {
        val currentSelected = selectedGroup
        return ((if (currentSelected > 0L) SagerDatabase.groupDao.getById(currentSelected) else null)
            ?: SagerDatabase.groupDao.allGroups().firstOrNull()
            ?: ProxyGroup(ungrouped = true).apply {
                id = SagerDatabase.groupDao.createGroup(this)
            }).also { group -> selectedGroup = group.id }
    }

    fun selectedGroupForImport(): Long {
        val current = currentGroup()
        if (current.type == GroupType.BASIC) return current.id
        return SagerDatabase.groupDao.allGroups().find { it.type == GroupType.BASIC }?.id
            ?: SagerDatabase.groupDao.createGroup(
                ProxyGroup(ungrouped = true)
            ).also { selectedGroup = it }
    }

    var appTheme by configurationStore.int(Key.APP_THEME)
    var nightTheme by configurationStore.stringToInt(Key.NIGHT_THEME)
    var serviceMode by configurationStore.string(Key.SERVICE_MODE) { Key.MODE_VPN }

    var domainStrategy by configurationStore.string(Key.DOMAIN_STRATEGY) { "AsIs" }
    var trafficSniffing by configurationStore.boolean(Key.TRAFFIC_SNIFFING) { true }
    var destinationOverride by configurationStore.boolean(Key.DESTINATION_OVERRIDE)
    var resolveDestination by configurationStore.boolean(Key.RESOLVE_DESTINATION)


    var tcpKeepAliveInterval by configurationStore.stringToInt(Key.TCP_KEEP_ALIVE_INTERVAL) { 15 }

    var bypassLan by configurationStore.boolean(Key.BYPASS_LAN)
    var bypassLanInCoreOnly by configurationStore.boolean(Key.BYPASS_LAN_IN_CORE_ONLY)

    var allowAccess by configurationStore.boolean(Key.ALLOW_ACCESS)
    var speedInterval by configurationStore.stringToInt(Key.SPEED_INTERVAL)

    // https://github.com/SagerNet/SagerNet/issues/180
    var remoteDns by configurationStore.stringNotBlack(Key.REMOTE_DNS) { "tls://dns.google" }
    var directDns by configurationStore.stringNotBlack(Key.DIRECT_DNS) { "tls+local://dot.pub" }
    var useLocalDnsAsDirectDns by configurationStore.boolean(Key.USE_LOCAL_DNS_AS_DIRECT_DNS) { true }
    var hosts by configurationStore.string(Key.DNS_HOSTS)
    var enableDnsRouting by configurationStore.boolean(Key.ENABLE_DNS_ROUTING) { true }
    var disableDnsExpire by configurationStore.boolean(Key.DISABLE_DNS_EXPIRE)

    var securityAdvisory by configurationStore.boolean(Key.SECURITY_ADVISORY) { true }
    var rulesProvider by configurationStore.stringToInt(Key.RULES_PROVIDER)
    var enableLog by configurationStore.boolean(Key.ENABLE_LOG) { BuildConfig.DEBUG }
    var enablePcap by configurationStore.boolean(Key.ENABLE_PCAP)
    var acquireWakeLock by configurationStore.boolean(Key.ACQUIRE_WAKE_LOCK)

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
    var transproxyPort: Int
        get() = getLocalPort(Key.TRANSPROXY_PORT, 9200)
        set(value) = saveLocalPort(Key.TRANSPROXY_PORT, value)

    fun initGlobal() {
        if (configurationStore.getString(Key.SOCKS_PORT) == null) {
            socksPort = socksPort
        }
        if (configurationStore.getString(Key.LOCAL_DNS_PORT) == null) {
            localDNSPort = localDNSPort
        }
        if (configurationStore.getString(Key.HTTP_PORT) == null) {
            httpPort = httpPort
        }
        if (configurationStore.getString(Key.TRANSPROXY_PORT) == null) {
            transproxyPort = transproxyPort
        }
        if (configurationStore.getString(Key.DNS_HOSTS) == null) {
            hosts = hosts
        }
        if (configurationStore.getString(Key.REMOTE_DNS).isNullOrBlank()) {
            remoteDns = remoteDns
        }
        if (configurationStore.getString(Key.DIRECT_DNS).isNullOrBlank()) {
            directDns = directDns
        }
        if (configurationStore.getString(Key.MTU).isNullOrBlank()) {
            mtu = mtu
        }
    }


    private fun getLocalPort(key: String, default: Int): Int {
        return parsePort(configurationStore.getString(key), default + userIndex)
    }

    private fun saveLocalPort(key: String, value: Int) {
        configurationStore.putString(key, "$value")
    }

    var ipv6Mode by configurationStore.stringToInt(Key.IPV6_MODE) { IPv6Mode.ENABLE }

    var meteredNetwork by configurationStore.boolean(Key.METERED_NETWORK)
    var proxyApps by configurationStore.boolean(Key.PROXY_APPS)
    var bypass by configurationStore.boolean(Key.BYPASS_MODE) { true }
    var individual by configurationStore.string(Key.INDIVIDUAL)
    var enableMux by configurationStore.boolean(Key.ENABLE_MUX)
    var enableMuxForAll by configurationStore.boolean(Key.ENABLE_MUX_FOR_ALL)
    var muxConcurrency by configurationStore.stringToInt(Key.MUX_CONCURRENCY) { 8 }
    var showDirectSpeed by configurationStore.boolean(Key.SHOW_DIRECT_SPEED)

    val persistAcrossReboot by configurationStore.boolean(Key.PERSIST_ACROSS_REBOOT) { true }
    val canToggleLocked: Boolean get() = false//configurationStore.getBoolean(Key.DIRECT_BOOT_AWARE) == true
    val directBootAware: Boolean get() = SagerNet.directBootSupported && canToggleLocked

    var requireHttp by configurationStore.boolean(Key.REQUIRE_HTTP) { false }
    var appendHttpProxy by configurationStore.boolean(Key.APPEND_HTTP_PROXY) { true }
    var requireTransproxy by configurationStore.boolean(Key.REQUIRE_TRANSPROXY)
    var transproxyMode by configurationStore.stringToInt(Key.TRANSPROXY_MODE)
    var connectionTestURL by configurationStore.string(Key.CONNECTION_TEST_URL) { CONNECTION_TEST_URL }
    var alwaysShowAddress by configurationStore.boolean(Key.ALWAYS_SHOW_ADDRESS)

    var tunImplementation by configurationStore.stringToInt(Key.TUN_IMPLEMENTATION) { TunImplementation.SYSTEM }

    var useUpstreamInterfaceMTU by configurationStore.boolean(Key.USE_UPSTREAM_INTERFACE_MTU) { true }
    var mtu by configurationStore.stringToInt(Key.MTU) { VpnService.DEFAULT_MTU }

    var appTrafficStatistics by configurationStore.boolean(Key.APP_TRAFFIC_STATISTICS)
    var profileTrafficStatistics by configurationStore.boolean(Key.PROFILE_TRAFFIC_STATISTICS) { true }

    // protocol

    var providerTrojan by configurationStore.stringToInt(Key.PROVIDER_TROJAN)
    var providerRootCA by configurationStore.stringToInt(Key.PROVIDER_ROOT_CA)
    var providerInstaller by configurationStore.stringToInt(Key.PROVIDER_INSTALLER)

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
    var serverMethod by profileCacheStore.string(Key.SERVER_METHOD)
    var serverPlugin by profileCacheStore.string(Key.SERVER_PLUGIN)

    var serverProtocol by profileCacheStore.string(Key.SERVER_PROTOCOL)
    var serverProtocolParam by profileCacheStore.string(Key.SERVER_PROTOCOL_PARAM)
    var serverObfs by profileCacheStore.string(Key.SERVER_OBFS)
    var serverObfsParam by profileCacheStore.string(Key.SERVER_OBFS_PARAM)

    var serverUserId by profileCacheStore.string(Key.SERVER_USER_ID)
    var serverSecurity by profileCacheStore.string(Key.SERVER_SECURITY)
    var serverNetwork by profileCacheStore.string(Key.SERVER_NETWORK)
    var serverHeader by profileCacheStore.string(Key.SERVER_HEADER)
    var serverHost by profileCacheStore.string(Key.SERVER_HOST)
    var serverPath by profileCacheStore.string(Key.SERVER_PATH)
    var serverSNI by profileCacheStore.string(Key.SERVER_SNI)
    var serverTLS by profileCacheStore.boolean(Key.SERVER_TLS)
    var serverEncryption by profileCacheStore.string(Key.SERVER_ENCRYPTION)
    var serverALPN by profileCacheStore.string(Key.SERVER_ALPN)
    var serverCertificates by profileCacheStore.string(Key.SERVER_CERTIFICATES)
    var serverPinnedCertificateChain by profileCacheStore.string(Key.SERVER_PINNED_CERTIFICATE_CHAIN)
    var serverFlow by profileCacheStore.string(Key.SERVER_FLOW)
    var serverQuicSecurity by profileCacheStore.string(Key.SERVER_QUIC_SECURITY)
    var serverWsMaxEarlyData by profileCacheStore.stringToInt(Key.SERVER_WS_MAX_EARLY_DATA)
    var serverWsBrowserForwarding by profileCacheStore.boolean(Key.SERVER_WS_BROWSER_FORWARDING)
    var serverEarlyDataHeaderName by profileCacheStore.string(Key.SERVER_EARLY_DATA_HEADER_NAME)
    var serverHeaders by profileCacheStore.string(Key.SERVER_HEADERS)
    var serverAllowInsecure by profileCacheStore.boolean(Key.SERVER_ALLOW_INSECURE)
    var serverPacketEncoding by profileCacheStore.stringToInt(Key.SERVER_PACKET_ENCODING)

    var serverVMessExperimentalAuthenticatedLength by profileCacheStore.boolean(Key.SERVER_VMESS_EXPERIMENTAL_AUTHENTICATED_LENGTH)
    var serverVMessExperimentalNoTerminationSignal by profileCacheStore.boolean(Key.SERVER_VMESS_EXPERIMENTAL_NO_TERMINATION_SIGNAL)

    var serverAuthType by profileCacheStore.stringToInt(Key.SERVER_AUTH_TYPE)
    var serverUploadSpeed by profileCacheStore.stringToInt(Key.SERVER_UPLOAD_SPEED)
    var serverDownloadSpeed by profileCacheStore.stringToInt(Key.SERVER_DOWNLOAD_SPEED)
    var serverStreamReceiveWindow by profileCacheStore.stringToIntIfExists(Key.SERVER_STREAM_RECEIVE_WINDOW)
    var serverConnectionReceiveWindow by profileCacheStore.stringToIntIfExists(Key.SERVER_CONNECTION_RECEIVE_WINDOW)
    var serverDisableMtuDiscovery by profileCacheStore.boolean(Key.SERVER_DISABLE_MTU_DISCOVERY)

    var serverProtocolVersion by profileCacheStore.stringToInt(Key.SERVER_PROTOCOL)
    var serverPrivateKey by profileCacheStore.string(Key.SERVER_PRIVATE_KEY)
    var serverLocalAddress by profileCacheStore.string(Key.SERVER_LOCAL_ADDRESS)
    var serverInsecureConcurrency by profileCacheStore.stringToInt(Key.SERVER_INSECURE_CONCURRENCY)
    var serverMTU by profileCacheStore.stringToInt(Key.SERVER_MTU)
    var serverReducedIvHeadEntropy by profileCacheStore.boolean(Key.SERVER_REDUCED_IV_HEAD_ENTROPY)
    var serverUoT by profileCacheStore.boolean(Key.SERVER_UDP_OVER_TCP)
    var serverWithoutBrookProtocol by profileCacheStore.boolean(Key.SERVER_WITHOUT_BROOK_PROTOCOL)
    var serverGrpcMode by profileCacheStore.string(Key.SERVER_GRPC_MODE)
    var serverEncryptedProtocolExtension by profileCacheStore.boolean(Key.SERVER_ENCRYPTED_PROTOCOL_EXTENSION)

    var serverUDPRelayMode by profileCacheStore.string(Key.SERVER_UDP_RELAY_MODE)
    var serverCongestionController by profileCacheStore.string(Key.SERVER_CONGESTION_CONTROLLER)
    var serverDisableSNI by profileCacheStore.boolean(Key.SERVER_DISABLE_SNI)
    var serverReduceRTT by profileCacheStore.boolean(Key.SERVER_REDUCE_RTT)

    var balancerType by profileCacheStore.stringToInt(Key.BALANCER_TYPE)
    var balancerGroup by profileCacheStore.stringToLong(Key.BALANCER_GROUP)
    var balancerStrategy by profileCacheStore.string(Key.BALANCER_STRATEGY)
    var balancerProbeUrl by profileCacheStore.string(Key.PROBE_URL)
    var balancerProbeInterval by profileCacheStore.stringToInt(Key.PROBE_INTERVAL) { 300 }


    var routeName by profileCacheStore.string(Key.ROUTE_NAME)
    var routeDomain by profileCacheStore.string(Key.ROUTE_DOMAIN)
    var routeIP by profileCacheStore.string(Key.ROUTE_IP)
    var routePort by profileCacheStore.string(Key.ROUTE_PORT)
    var routeSourcePort by profileCacheStore.string(Key.ROUTE_SOURCE_PORT)
    var routeNetwork by profileCacheStore.string(Key.ROUTE_NETWORK)
    var routeSource by profileCacheStore.string(Key.ROUTE_SOURCE)
    var routeProtocol by profileCacheStore.string(Key.ROUTE_PROTOCOL)
    var routeAttrs by profileCacheStore.string(Key.ROUTE_ATTRS)
    var routeOutbound by profileCacheStore.stringToInt(Key.ROUTE_OUTBOUND)
    var routeOutboundRule by profileCacheStore.long(Key.ROUTE_OUTBOUND_RULE)
    var routeReverse by profileCacheStore.boolean(Key.ROUTE_REVERSE)
    var routeRedirect by profileCacheStore.string(Key.ROUTE_REDIRECT)
    var routePackages by profileCacheStore.string(Key.ROUTE_PACKAGES)
    var routeNetworkType by profileCacheStore.string(Key.ROUTE_NETWORK_TYPE)
    var routeSSID by profileCacheStore.string(Key.ROUTE_SSID)

    var serverConfig by profileCacheStore.string(Key.SERVER_CONFIG)

    var groupName by profileCacheStore.string(Key.GROUP_NAME)
    var groupType by profileCacheStore.stringToInt(Key.GROUP_TYPE)
    var groupOrder by profileCacheStore.stringToInt(Key.GROUP_ORDER)

    var subscriptionType by profileCacheStore.stringToInt(Key.SUBSCRIPTION_TYPE)
    var subscriptionLink by profileCacheStore.string(Key.SUBSCRIPTION_LINK)
    var subscriptionToken by profileCacheStore.string(Key.SUBSCRIPTION_TOKEN)
    var subscriptionForceResolve by profileCacheStore.boolean(Key.SUBSCRIPTION_FORCE_RESOLVE)
    var subscriptionDeduplication by profileCacheStore.boolean(Key.SUBSCRIPTION_DEDUPLICATION)
    var subscriptionUpdateWhenConnectedOnly by profileCacheStore.boolean(Key.SUBSCRIPTION_UPDATE_WHEN_CONNECTED_ONLY)
    var subscriptionUserAgent by profileCacheStore.string(Key.SUBSCRIPTION_USER_AGENT)
    var subscriptionAutoUpdate by profileCacheStore.boolean(Key.SUBSCRIPTION_AUTO_UPDATE)
    var subscriptionAutoUpdateDelay by profileCacheStore.stringToInt(Key.SUBSCRIPTION_AUTO_UPDATE_DELAY) { 360 }

    var taskerAction by profileCacheStore.stringToInt(Key.TASKER_ACTION)
    var taskerProfile by profileCacheStore.stringToInt(Key.TASKER_PROFILE)
    var taskerProfileId by profileCacheStore.long(Key.TASKER_PROFILE_ID) { -1L }

    var rulesFirstCreate by profileCacheStore.boolean("rulesFirstCreate")

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.PROFILE_ID -> if (directBootAware) DirectBoot.update()
        }
    }
}

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

package io.nekohasekai.sagernet

const val CONNECTION_TEST_URL = "https://api.v2fly.org/checkConnection.svgz"

object Key {

    const val DB_PUBLIC = "configuration.db"
    const val DB_PROFILE = "sager_net.db"
    const val DISABLE_AEAD = "V2RAY_VMESS_AEAD_DISABLED"

    const val PERSIST_ACROSS_REBOOT = "isAutoConnect"
    const val DIRECT_BOOT_AWARE = "directBootAware"

    const val APP_THEME = "appTheme"
    const val NIGHT_THEME = "nightTheme"
    const val SERVICE_MODE = "serviceMode"
    const val MODE_VPN = "vpn"
    const val MODE_PROXY = "proxy"

    const val REMOTE_DNS = "remoteDns"
    const val DIRECT_DNS = "directDns"
    const val ENABLE_DNS_ROUTING = "enableDnsRouting"
    const val ENABLE_FAKEDNS = "enableFakeDns"
    const val DNS_HOSTS = "dnsHosts"

    const val IPV6_MODE = "ipv6Mode"

    const val PROXY_APPS = "proxyApps"
    const val BYPASS_MODE = "bypassMode"
    const val INDIVIDUAL = "individual"
    const val METERED_NETWORK = "meteredNetwork"

    const val DOMAIN_STRATEGY = "domainStrategy"
    const val DOMAIN_MATCHER = "domainMatcher"
    const val TRAFFIC_SNIFFING = "trafficSniffing"
    const val BYPASS_LAN = "bypassLan"
    const val BYPASS_LAN_IN_CORE_ONLY = "bypassLanInCoreOnly"

    const val SOCKS_PORT = "socksPort"
    const val ALLOW_ACCESS = "allowAccess"
    const val SPEED_INTERVAL = "speedInterval"
    const val SHOW_DIRECT_SPEED = "showDirectSpeed"
    const val LOCAL_DNS_PORT = "portLocalDns"

    const val REQUIRE_HTTP = "requireHttp"
    const val APPEND_HTTP_PROXY = "appendHttpProxy"
    const val HTTP_PORT = "httpPort"

    const val REQUIRE_TRANSPROXY = "requireTransproxy"
    const val TRANSPROXY_MODE = "transproxyMode"
    const val TRANSPROXY_PORT = "transproxyPort"

    const val CONNECTION_TEST_URL = "connectionTestURL"
    const val PROBE_INTERVAL = "probeInterval"

    const val ENABLE_MUX = "enableMux"
    const val ENABLE_MUX_FOR_ALL = "enableMuxForAll"
    const val MUX_CONCURRENCY = "muxConcurrency"
    const val SHOW_STOP_BUTTON = "showStopButton"
    const val SECURITY_ADVISORY = "securityAdvisory"
    const val TCP_KEEP_ALIVE_INTERVAL = "tcpKeepAliveInterval"
    const val RULES_PROVIDER = "rulesProvider"
    const val ENABLE_LOG = "enableLog"

    const val API_PORT = "apiPort"
    const val ALWAYS_SHOW_ADDRESS = "alwaysShowAddress"
    const val ENABLE_EXPERIMENTAL_TUN = "enableExperimentalTun"

    const val VPN_MODE = "vpnMode"
    const val MULTI_THREAD_FORWARD = "multiThreadForward"
    const val ICMP_ECHO_STRATEGY = "icmpEchoStrategy"
    const val ICMP_ECHO_REPLY_DELAY = "icmpEchoReplyDelay"
    const val IP_OTHER_STRATEGY = "ipOtherStrategy"

    const val PROVIDER_TROJAN = "providerTrojan"
    const val PROVIDER_SS_AEAD = "providerShadowsocksAEAD"

    const val PROFILE_DIRTY = "profileDirty"
    const val PROFILE_ID = "profileId"
    const val PROFILE_NAME = "profileName"
    const val PROFILE_GROUP = "profileGroup"
    const val PROFILE_STARTED = "profileStarted"
    const val PROFILE_CURRENT = "profileCurrent"

    const val SERVER_ADDRESS = "serverAddress"
    const val SERVER_PORT = "serverPort"
    const val SERVER_USERNAME = "serverUsername"
    const val SERVER_PASSWORD = "serverPassword"
    const val SERVER_METHOD = "serverMethod"
    const val SERVER_PLUGIN = "serverPlugin"
    const val SERVER_PLUGIN_CONFIGURE = "serverPluginConfigure"
    const val SERVER_PASSWORD1 = "serverPassword1"

    const val SERVER_PROTOCOL = "serverProtocol"
    const val SERVER_PROTOCOL_PARAM = "serverProtocolParam"
    const val SERVER_OBFS = "serverObfs"
    const val SERVER_OBFS_PARAM = "serverObfsParam"

    const val SERVER_USER_ID = "serverUserId"
    const val SERVER_ALTER_ID = "serverAlterId"
    const val SERVER_SECURITY = "serverSecurity"
    const val SERVER_NETWORK = "serverNetwork"
    const val SERVER_HEADER = "serverHeader"
    const val SERVER_HOST = "serverHost"
    const val SERVER_PATH = "serverPath"
    const val SERVER_SNI = "serverSNI"
    const val SERVER_TLS = "serverTLS"
    const val SERVER_ENCRYPTION = "serverEncryption"
    const val SERVER_ALPN = "serverALPN"
    const val SERVER_CERTIFICATES = "serverCertificates"
    const val SERVER_PINNED_CERTIFICATE_CHAIN = "serverPinnedCertificateChain"
    const val SERVER_QUIC_SECURITY = "serverQuicSecurity"
    const val SERVER_WS_MAX_EARLY_DATA = "serverWsMaxEarlyData"
    const val SERVER_WS_BROWSER_FORWARDING = "serverWsBrowserForwarding"
    const val SERVER_EARLY_DATA_HEADER_NAME = "serverEarlyDataHeaderName"
    const val SERVER_CONFIG = "serverConfig"

    const val SERVER_SECURITY_CATEGORY = "serverSecurityCategory"
    const val SERVER_WS_CATEGORY = "serverWsCategory"
    const val SERVER_SS_CATEGORY = "serverSsCategory"
    const val SERVER_HEADERS = "serverHeaders"
    const val SERVER_ALLOW_INSECURE = "serverAllowInsecure"

    const val BALANCER_TYPE = "balancerType"
    const val BALANCER_GROUP = "balancerGroup"
    const val BALANCER_STRATEGY = "balancerStrategy"

    const val ROUTE_NAME = "routeName"
    const val ROUTE_DOMAIN = "routeDomain"
    const val ROUTE_IP = "routeIP"
    const val ROUTE_PORT = "routePort"
    const val ROUTE_SOURCE_PORT = "routeSourcePort"
    const val ROUTE_NETWORK = "routeNetwork"
    const val ROUTE_SOURCE = "routeSource"
    const val ROUTE_PROTOCOL = "routeProtocol"
    const val ROUTE_ATTRS = "routeAttrs"
    const val ROUTE_OUTBOUND = "routeOutbound"
    const val ROUTE_OUTBOUND_RULE = "routeOutboundRule"
    const val ROUTE_REVERSE = "routeReverse"
    const val ROUTE_REDIRECT = "routeRedirect"
    const val ROUTE_PACKAGES = "routePackages"

    const val GROUP_NAME = "groupName"
    const val GROUP_TYPE = "groupType"

    const val GROUP_SUBSCRIPTION = "groupSubscription"
    const val SUBSCRIPTION_TYPE = "subscriptionType"
    const val SUBSCRIPTION_LINK = "subscriptionLink"
    const val SUBSCRIPTION_TOKEN = "subscriptionToken"
    const val SUBSCRIPTION_FORCE_RESOLVE = "subscriptionForceResolve"
    const val SUBSCRIPTION_DEDUPLICATION = "subscriptionDeduplication"
    const val SUBSCRIPTION_FORCE_VMESS_AEAD = "subscriptionForceVMessAEAD"
    const val SUBSCRIPTION_UPDATE = "subscriptionUpdate"
    const val SUBSCRIPTION_UPDATE_WHEN_CONNECTED_ONLY = "subscriptionUpdateWhenConnectedOnly"
    const val SUBSCRIPTION_USER_AGENT = "subscriptionUserAgent"
    const val SUBSCRIPTION_AUTO_UPDATE = "subscriptionAutoUpdate"
    const val SUBSCRIPTION_AUTO_UPDATE_DELAY = "subscriptionAutoUpdateDelay"

}

object TrojanProvider {
    const val V2RAY = 0
    const val TROJAN = 1
    const val TROJAN_GO = 2
}

object ShadowsocksAEADProvider {
    const val V2RAY = 0
    const val SHADOWSOCKS_RUST = 1
}

object IPv6Mode {
    const val DISABLE = 0
    const val ENABLE = 1
    const val PREFER = 2
    const val ONLY = 3
}

object VpnMode {
    const val TUN2SOCKS = 0
    const val EXPERIMENTAL_FORWARDING = 1
}

object PacketStrategy {
    const val DIRECT = 0
    const val DROP = 1
    const val REPLY = 2
}

object GroupType {
    const val BASIC = 0
    const val SUBSCRIPTION = 1
}

object SubscriptionType {
    const val RAW = 0
    const val OOCv1 = 1
    const val SIP008 = 2
}

object ExtraType {
    const val NONE = 0
    const val OOCv1 = 1
    const val SIP008 = 2
}

object Action {
    const val SERVICE = "io.nekohasekai.sagernet.SERVICE"
    const val CLOSE = "io.nekohasekai.sagernet.CLOSE"
    const val RELOAD = "io.nekohasekai.sagernet.RELOAD"
    const val ABORT = "io.nekohasekai.sagernet.ABORT"

    const val EXTRA_PROFILE_ID = "io.nekohasekai.sagernet.EXTRA_PROFILE_ID"
}
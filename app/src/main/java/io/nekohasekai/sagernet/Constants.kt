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

object Key {

    const val DB_PUBLIC = "configuration.db"
    const val DB_PROFILE = "sager_net.db"
    const val DISABLE_AEAD = "V2RAY_VMESS_AEAD_DISABLED"

    const val PERSIST_ACROSS_REBOOT = "isAutoConnect"
    const val DIRECT_BOOT_AWARE = "directBootAware"

    const val SERVICE_MODE = "serviceMode"
    const val MODE_VPN = "vpn"
    const val MODE_PROXY = "proxy"
    const val MODE_TRANS = "transproxy"

    const val REMOTE_DNS = "remoteDns"
    const val ENABLE_LOCAL_DNS = "enableLocalDns"
    const val LOCAL_DNS_PORT = "portLocalDns"
    const val DOMESTIC_DNS = "domesticDns"

    const val IPV6_ROUTE = "ipv6Route"
    const val PREFER_IPV6 = "preferIpv6"

    const val PROXY_APPS = "proxyApps"
    const val BYPASS_MODE = "bypassMode"
    const val METERED_NETWORK = "meteredNetwork"

    const val DOMAIN_STRATEGY = "domainStrategy"
    const val DOMAIN_MATCHER = "domainMatcher"
    const val TRAFFIC_SNIFFING = "trafficSniffing"
    const val BYPASS_LAN = "bypassLan"
    const val ROUTE_CHINA = "routeChina"
    const val BLOCK_ADS = "blockAds"

    const val SOCKS_PORT = "socksPort"
    const val FORCE_SHADOWSOCKS_RUST = "forceShadowsocksRust"
    const val REQUIRE_HTTP = "requireHttp"
    const val HTTP_PORT = "httpPort"
    const val ALLOW_ACCESS = "allowAccess"
    const val SPEED_INTERVAL = "speedInterval"

    const val ENABLE_MUX = "enableMux"
    const val MUX_CONCURRENCY = "muxConcurrency"
    const val SHOW_STOP_BUTTON = "showStopButton"
    const val SECURITY_ADVISORY = "securityAdvisory"

    const val PROFILE_DIRTY = "profileDirty"
    const val PROFILE_ID = "profileId"
    const val PROFILE_NAME = "profileName"
    const val PROFILE_GROUP = "profileGroup"

    const val SERVER_ADDRESS = "serverAddress"
    const val SERVER_PORT = "serverPort"
    const val SERVER_USERNAME = "serverUsername"
    const val SERVER_PASSWORD = "serverPassword"
    const val SERVER_UDP = "serverUdp"
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
    const val SERVER_FLOW = "serverFlow"
    const val SERVER_QUIC_SECURITY = "serverQuicSecurity"
    const val SERVER_WS_MAX_EARLY_DATA = "serverWsMaxEarlyData"
    const val SERVER_WS_BROWSER_FORWARDING = "serverWsBrowserForwarding"
    const val SERVER_WS_CATEGORY = "serverWsCategory"
    const val SERVER_SS_CATEGORY = "serverSsCategory"

}

object Action {
    const val SERVICE = "io.nekohasekai.sagernet.SERVICE"
    const val CLOSE = "io.nekohasekai.sagernet.CLOSE"
    const val RELOAD = "io.nekohasekai.sagernet.RELOAD"
    const val ABORT = "io.nekohasekai.sagernet.ABORT"

    const val EXTRA_PROFILE_ID = "io.nekohasekai.sagernet.EXTRA_PROFILE_ID"
}

package io.nekohasekai.sagernet

object Key {

    const val DB_PUBLIC = "configuration.db"
    const val DB_PROFILE = "sager_net.db"
    const val DISABLE_AEAD = "V2RAY_VMESS_AEAD_DISABLED"

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

    const val ROUTE_MODE = "routeMode"
    const val SOCKS_PORT = "socksPort"

    const val ALLOW_ACCESS = "allowAccess"

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

}

object RouteMode {
    const val ALL = "all"
    const val BYPASS_LAN = "bypass-lan"
    const val BYPASS_CHINA = "bypass-china"
    const val BYPASS_LAN_CHINA = "bypass-lan-china"
}

object Action {
    const val SERVICE = "io.nekohasekai.sagernet.SERVICE"
    const val CLOSE = "io.nekohasekai.sagernet.CLOSE"
    const val RELOAD = "io.nekohasekai.sagernet.RELOAD"
    const val ABORT = "io.nekohasekai.sagernet.ABORT"

    const val EXTRA_PROFILE_ID = "io.nekohasekai.sagernet.EXTRA_PROFILE_ID"
}

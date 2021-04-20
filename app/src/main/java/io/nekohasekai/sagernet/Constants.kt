package io.nekohasekai.sagernet

object Key {

    const val DB_PUBLIC = "configuration.db"
    const val DB_PROFILE = "sager_net.db"
    const val DISABLE_AEAD = "V2RAY_VMESS_AEAD_DISABLED"

    const val SERVICE_MODE = "service_mode"
    const val MODE_VPN = 0
    const val MODE_PROXY = 1
    const val MODE_TRANS = 2

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

}

object Action {
    const val SERVICE = "com.github.shadowsocks.SERVICE"
    const val CLOSE = "com.github.shadowsocks.CLOSE"
    const val RELOAD = "com.github.shadowsocks.RELOAD"
    const val ABORT = "com.github.shadowsocks.ABORT"

    const val EXTRA_PROFILE_ID = "com.github.shadowsocks.EXTRA_PROFILE_ID"
}

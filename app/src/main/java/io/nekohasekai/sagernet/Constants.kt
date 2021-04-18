package io.nekohasekai.sagernet

object Key {

    const val DB_PUBLIC = "configuration.db"
    const val DB_PROFILE = "sager_net.db"
    const val DISABLE_AEAD = "V2RAY_VMESS_AEAD_DISABLED"

    const val SERVICE_MODE = "service_mode"
    const val MODE_VPN = 0
    const val MODE_PROXY = 1
    const val MODE_TRANS = 2

}

object Action {
    const val SERVICE = "com.github.shadowsocks.SERVICE"
    const val CLOSE = "com.github.shadowsocks.CLOSE"
    const val RELOAD = "com.github.shadowsocks.RELOAD"
    const val ABORT = "com.github.shadowsocks.ABORT"

    const val EXTRA_PROFILE_ID = "com.github.shadowsocks.EXTRA_PROFILE_ID"
}

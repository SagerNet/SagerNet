package com.github.shadowsocks.plugin

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet


object NoPlugin : Plugin() {
    override val id: String get() = ""
    override val label: CharSequence get() = SagerNet.application.getText(R.string.plugin_disabled)
}

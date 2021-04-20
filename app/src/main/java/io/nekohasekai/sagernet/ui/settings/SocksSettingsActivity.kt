package io.nekohasekai.sagernet.ui.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import cn.hutool.core.util.NumberUtil
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean

class SocksSettingsActivity : ProfileSettingsActivity<SOCKSBean>() {

    override val type = "socks"
    override fun createEntity() = SOCKSBean()

    override fun init() {
        DataStore.profileName = ""
        DataStore.serverAddress = ""
        DataStore.serverPort = "1080"
        DataStore.serverUsername = ""
        DataStore.serverPassword = ""
        DataStore.serverUdp = false
    }

    override fun init(bean: SOCKSBean) {
        DataStore.profileName = bean.name
        DataStore.serverAddress = bean.serverAddress
        DataStore.serverPort = "${bean.serverPort}"
        DataStore.serverUsername = bean.username
        DataStore.serverPassword = bean.password
        DataStore.serverUdp = bean.udp
    }

    override fun SOCKSBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort =
            DataStore.serverPort
                .takeIf { !it.isNullOrBlank() && NumberUtil.isInteger(it) }?.toInt()
                ?: 1080
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        udp = DataStore.serverUdp
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.socks_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}
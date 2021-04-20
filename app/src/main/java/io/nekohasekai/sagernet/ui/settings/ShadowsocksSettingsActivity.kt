package io.nekohasekai.sagernet.ui.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import cn.hutool.core.util.NumberUtil
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean

class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override val type = "ss"
    override fun createEntity() = ShadowsocksBean()

    override fun init() {
        init(ShadowsocksBean.DEFAULT_BEAN)
    }

    override fun init(bean: ShadowsocksBean) {
        DataStore.profileName = bean.name
        DataStore.serverAddress = bean.serverAddress
        DataStore.serverPort = "${bean.serverPort}"
        DataStore.serverMethod = bean.method
        DataStore.serverPassword = bean.password
    }

    override fun ShadowsocksBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort =
            DataStore.serverPort
                .takeIf { !it.isNullOrBlank() && NumberUtil.isInteger(it) }?.toInt()
                ?: 1080
        method = DataStore.serverMethod
        password = DataStore.serverPassword
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowsocks_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}
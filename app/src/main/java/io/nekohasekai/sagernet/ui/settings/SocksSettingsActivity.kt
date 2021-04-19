package io.nekohasekai.sagernet.ui.settings

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.ViewCompat
import androidx.preference.EditTextPreference
import cn.hutool.core.util.NumberUtil
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.EVENT_UPDATE_GROUP
import io.nekohasekai.sagernet.ktx.EVENT_UPDATE_PROFILE
import io.nekohasekai.sagernet.ktx.postNotification
import io.nekohasekai.sagernet.widget.ListListener

class SocksSettingsActivity : ProfileSettingsActivity<SOCKSBean>() {

    override fun createFragment() = SocksPreferenceFragment()

    override fun init(bean: SOCKSBean?) {
        DataStore.profileName = bean?.name ?: ""
        DataStore.serverAddress = bean?.serverAddress ?: ""
        DataStore.serverPort = (bean?.serverPort ?: 1080).toString()
        DataStore.serverUsername = bean?.username ?: ""
        DataStore.serverPassword = bean?.password ?: ""
        DataStore.serverUdp = bean?.udp ?: false
    }

    fun saveAndExit() {
        val editingId = DataStore.editingId
        if (editingId == 0L) {
            val editingGroup = DataStore.editingGroup
            // create new entity
            SagerDatabase.proxyDao.addProxy(ProxyEntity(
                groupId = editingGroup,
                type = "socks",
                socksBean = SOCKSBean().apply {
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
            ))
            postNotification(EVENT_UPDATE_GROUP, editingGroup)
        } else {
            val entity = SagerDatabase.proxyDao.getById(DataStore.editingId)
            if (entity == null) {
                finish()
                return
            }
            SagerDatabase.proxyDao.updateProxy(entity.apply {
                requireSOCKS().apply {
                    name = DataStore.profileName
                    serverAddress = DataStore.serverAddress
                    serverPort = DataStore.serverPort
                        .takeIf { !it.isNullOrBlank() && NumberUtil.isInteger(it) }?.toInt()
                        ?: 1080
                    username = DataStore.serverUsername
                    password = DataStore.serverPassword
                    udp = DataStore.serverUdp
                }
            })
            postNotification(EVENT_UPDATE_PROFILE, editingId)
        }
        finish()
    }

    class SocksPreferenceFragment : MyPreferenceFragmentCompat<SOCKSBean, SocksSettingsActivity>() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            addPreferencesFromResource(R.xml.socks_preferences)
            findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
                setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
            }
            findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
                summaryProvider = PasswordSummaryProvider
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(listView, ListListener)
        }

        override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
            R.id.action_delete -> {
                DeleteConfirmationDialogFragment().withArg(ProfileIdArg(DataStore.editingId,
                    DataStore.editingGroup))
                    .show(this)
                true
            }
            R.id.action_apply -> {
                activity.saveAndExit()
                true
            }
            else -> false
        }

    }

}
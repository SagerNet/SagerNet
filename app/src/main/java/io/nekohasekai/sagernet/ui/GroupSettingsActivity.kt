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

package io.nekohasekai.sagernet.ui

import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.preference.*
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.bg.SubscriptionUpdater
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.DirectBoot
import io.nekohasekai.sagernet.widget.ListListener
import kotlinx.parcelize.Parcelize

@Suppress("UNCHECKED_CAST")
class GroupSettingsActivity(
    @LayoutRes resId: Int = R.layout.layout_config_settings,
) : ThemedActivity(resId),
    OnPreferenceDataStoreChangeListener {

    fun ProxyGroup.init() {
        DataStore.groupName = name ?: ""
        DataStore.groupType = type
        val subscription = subscription ?: SubscriptionBean().applyDefaultValues()
        DataStore.subscriptionType = subscription.type
        DataStore.subscriptionLink = subscription.link
        DataStore.subscriptionToken = subscription.token
        DataStore.subscriptionForceResolve = subscription.forceResolve
        DataStore.subscriptionDeduplication = subscription.deduplication
        DataStore.subscriptionForceVMessAEAD = subscription.forceVMessAEAD
        DataStore.subscriptionUpdateWhenConnectedOnly = subscription.updateWhenConnectedOnly
        DataStore.subscriptionUserAgent = subscription.customUserAgent
        DataStore.subscriptionAutoUpdate = subscription.autoUpdate
        DataStore.subscriptionAutoUpdateDelay = subscription.autoUpdateDelay
    }

    fun ProxyGroup.serialize() {
        name = DataStore.groupName.takeIf { it.isNotBlank() }
            ?: "My group " + System.currentTimeMillis() / 1000
        type = DataStore.groupType

        val isSubscription = type == GroupType.SUBSCRIPTION
        if (isSubscription) {
            subscription = (subscription ?: SubscriptionBean().applyDefaultValues()).apply {
                type = DataStore.subscriptionType
                link = DataStore.subscriptionLink
                token = DataStore.subscriptionToken
                forceResolve = DataStore.subscriptionForceResolve
                deduplication = DataStore.subscriptionDeduplication
                forceVMessAEAD = DataStore.subscriptionForceVMessAEAD
                updateWhenConnectedOnly = DataStore.subscriptionUpdateWhenConnectedOnly
                customUserAgent = DataStore.subscriptionUserAgent
                autoUpdate = DataStore.subscriptionAutoUpdate
                autoUpdateDelay = DataStore.subscriptionAutoUpdateDelay
            }
        }
    }

    fun needSave(): Boolean {
        if (!DataStore.dirty) return false
        return true
    }

    fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.group_preferences)

        val groupType = findPreference<SimpleMenuPreference>(Key.GROUP_TYPE)!!
        val groupSubscription = findPreference<PreferenceCategory>(Key.GROUP_SUBSCRIPTION)!!
        val subscriptionUpdate = findPreference<PreferenceCategory>(Key.SUBSCRIPTION_UPDATE)!!

        fun updateGroupType(groupType: Int = DataStore.groupType) {
            val isSubscription = groupType == GroupType.SUBSCRIPTION
            groupSubscription.isVisible = isSubscription
            subscriptionUpdate.isVisible = isSubscription
        }
        updateGroupType()
        groupType.setOnPreferenceChangeListener { _, newValue ->
            updateGroupType((newValue as String).toInt())
            true
        }

        val subscriptionType = findPreference<SimpleMenuPreference>(Key.SUBSCRIPTION_TYPE)!!
        val subscriptionLink = findPreference<EditTextPreference>(Key.SUBSCRIPTION_LINK)!!
        val subscriptionToken = findPreference<EditTextPreference>(Key.SUBSCRIPTION_TOKEN)!!
        val subscriptionForceVMessAEAD =
            findPreference<SwitchPreference>(Key.SUBSCRIPTION_FORCE_VMESS_AEAD)!!

        fun updateSubscriptionType(subscriptionType: Int = DataStore.subscriptionType) {
            val isRaw = subscriptionType == SubscriptionType.RAW
            val isOOCv1 = subscriptionType == SubscriptionType.OOCv1

            subscriptionForceVMessAEAD.isVisible = isRaw
            subscriptionLink.isVisible = !isOOCv1
            subscriptionToken.isVisible = isOOCv1
        }
        updateSubscriptionType()
        subscriptionType.setOnPreferenceChangeListener { _, newValue ->
            updateSubscriptionType((newValue as String).toInt())
            true
        }

        val subscriptionAutoUpdate =
            findPreference<SwitchPreference>(Key.SUBSCRIPTION_AUTO_UPDATE)!!
        val subscriptionAutoUpdateDelay =
            findPreference<EditTextPreference>(Key.SUBSCRIPTION_AUTO_UPDATE_DELAY)!!
        subscriptionAutoUpdateDelay.isEnabled = subscriptionAutoUpdate.isChecked
        subscriptionAutoUpdate.setOnPreferenceChangeListener { _, newValue ->
            subscriptionAutoUpdateDelay.isEnabled = (newValue as Boolean)
            true
        }
    }

    fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
    }

    fun PreferenceFragmentCompat.displayPreferenceDialog(preference: Preference): Boolean {
        return false
    }

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    (requireActivity() as GroupSettingsActivity).saveAndExit()
                }
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class GroupIdArg(val groupId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<GroupIdArg, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_group_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    GroupManager.deleteGroup(arg.groupId)
                }
                requireActivity().finish()
            }
            setNegativeButton(R.string.no, null)
        }
    }

    companion object {
        const val EXTRA_GROUP_ID = "id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.group_settings)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) {
            val editingId = intent.getLongExtra(EXTRA_GROUP_ID, 0L)
            DataStore.editingId = editingId
            runOnDefaultDispatcher {
                if (editingId == 0L) {
                    ProxyGroup().init()
                } else {
                    val entity = SagerDatabase.groupDao.getById(editingId)
                    if (entity == null) {
                        onMainDispatcher {
                            finish()
                        }
                        return@runOnDefaultDispatcher
                    }
                    entity.init()
                }

                onMainDispatcher {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.settings, MyPreferenceFragmentCompat().apply {
                            activity = this@GroupSettingsActivity
                        })
                        .commit()

                    DataStore.dirty = false
                    DataStore.profileCacheStore.registerChangeListener(this@GroupSettingsActivity)
                }
            }

        }

    }

    suspend fun saveAndExit() {

        val editingId = DataStore.editingId
        if (editingId == 0L) {
            GroupManager.createGroup(ProxyGroup().apply { serialize() })
        } else if (needSave()) {
            val entity = SagerDatabase.groupDao.getById(DataStore.editingId)
            if (entity == null) {
                finish()
                return
            }
            GroupManager.updateGroup(entity.apply { serialize() })
        }

        SubscriptionUpdater.reconfigureUpdater()
        if (editingId == DataStore.selectedProxy && DataStore.directBootAware) DirectBoot.update()
        finish()

    }

    val child by lazy { supportFragmentManager.findFragmentById(R.id.settings) as MyPreferenceFragmentCompat }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = child.onOptionsItemSelected(item)

    override fun onBackPressed() {
        if (needSave()) {
            UnsavedChangesDialogFragment().apply { key() }.show(supportFragmentManager, null)
        } else super.onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key != Key.PROFILE_DIRTY) {
            DataStore.dirty = true
        }
    }

    class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {

        lateinit var activity: GroupSettingsActivity

        override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            activity.apply {
                createPreferences(savedInstanceState, rootKey)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            ViewCompat.setOnApplyWindowInsetsListener(listView, ListListener)

            activity.apply {
                viewCreated(view, savedInstanceState)
            }
        }

        override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
            R.id.action_delete -> {
                if (DataStore.editingId == 0L) {
                    requireActivity().finish()
                } else {
                    DeleteConfirmationDialogFragment().apply {
                        arg(GroupIdArg(DataStore.editingId))
                        key()
                    }.show(parentFragmentManager, null)
                }
                true
            }
            R.id.action_apply -> {
                runOnDefaultDispatcher {
                    activity.saveAndExit()
                }
                true
            }
            else -> false
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            activity.apply {
                if (displayPreferenceDialog(preference)) return
            }
            super.onDisplayPreferenceDialog(preference)
        }

    }

    object PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {

        override fun provideSummary(preference: EditTextPreference): CharSequence {
            return if (preference.text.isNullOrBlank()) {
                preference.context.getString(androidx.preference.R.string.not_set)
            } else {
                "\u2022".repeat(preference.text.length)
            }
        }

    }

}
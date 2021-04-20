package io.nekohasekai.sagernet.ui.settings

import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.Empty
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.AlertDialogFragment
import io.nekohasekai.sagernet.widget.ListListener
import kotlinx.parcelize.Parcelize

@Suppress("UNCHECKED_CAST")
abstract class ProfileSettingsActivity<T : AbstractBean> : AppCompatActivity(),
    OnPreferenceDataStoreChangeListener {

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    (requireActivity() as ProfileSettingsActivity<*>).saveAndExit()
                }
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class ProfileIdArg(val profileId: Long, val groupId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<ProfileIdArg, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_confirm_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    ProfileManager.deleteProfile(arg.groupId, arg.profileId)
                }
                requireActivity().finish()
            }
            setNegativeButton(R.string.no, null)
        }
    }

    companion object {
        const val REQUEST_UNSAVED_CHANGES = 10
        const val EXTRA_PROFILE_ID = "id"
        const val EXTRA_GROUP_ID = "group"
    }

    abstract val type: String
    abstract fun createEntity(): T
    abstract fun init()
    abstract fun init(bean: T)
    abstract fun T.serialize()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.profile_config)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) {
            val editingId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
            DataStore.dirty = false
            DataStore.editingId = editingId
            if (editingId == 0L) {
                val editingGroup = intent.getLongExtra(EXTRA_GROUP_ID, 0L)
                DataStore.editingGroup = editingGroup
                init()
            } else {
                val proxyEntity = SagerDatabase.proxyDao.getById(editingId)
                if (proxyEntity == null) {
                    finish()
                    return
                }
                DataStore.editingGroup = proxyEntity.groupId
                init(proxyEntity.requireBean() as T)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings,
                    MyPreferenceFragmentCompat().apply { activity = this@ProfileSettingsActivity })
                .commit()
        }
        DataStore.profileCacheStore.registerChangeListener(this)

    }

    suspend fun saveAndExit() {

        val editingId = DataStore.editingId
        if (editingId == 0L) {
            val editingGroup = DataStore.editingGroup
            ProfileManager.createProfile(editingGroup, createEntity().apply { serialize() })
        } else {
            val entity = SagerDatabase.proxyDao.getById(DataStore.editingId)
            if (entity == null) {
                finish()
                return
            }
            ProfileManager.updateProfile(entity.apply { (requireBean() as T).serialize() })
        }
        finish()

    }

    val child by lazy { supportFragmentManager.findFragmentById(R.id.settings) as MyPreferenceFragmentCompat }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = child.onOptionsItemSelected(item)

    override fun onBackPressed() {
        if (DataStore.dirty) UnsavedChangesDialogFragment().show(child, REQUEST_UNSAVED_CHANGES)
        else super.onBackPressed()
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

    abstract fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    )

    open fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
    }

    open fun PreferenceFragmentCompat.displayPreferenceDialog(preference: Preference): Boolean {
        return false
    }

    class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {

        lateinit var activity: ProfileSettingsActivity<*>

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
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
                DeleteConfirmationDialogFragment().withArg(ProfileIdArg(DataStore.editingId,
                    DataStore.editingGroup))
                    .show(this)
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
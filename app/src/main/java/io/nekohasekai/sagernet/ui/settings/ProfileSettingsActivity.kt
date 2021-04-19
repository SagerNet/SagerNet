package io.nekohasekai.sagernet.ui.settings

import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.EVENT_UPDATE_GROUP
import io.nekohasekai.sagernet.ktx.Empty
import io.nekohasekai.sagernet.ktx.postNotification
import io.nekohasekai.sagernet.utils.AlertDialogFragment
import kotlinx.parcelize.Parcelize

@Suppress("UNCHECKED_CAST")
abstract class ProfileSettingsActivity<T : AbstractBean> : AppCompatActivity() {

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(R.string.yes, listener)
            setNegativeButton(R.string.no, listener)
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class ProfileIdArg(val profileId: Long, val groupId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<ProfileIdArg, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_confirm_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                SagerDatabase.proxyDao.deleteById(arg.profileId)
                postNotification(EVENT_UPDATE_GROUP, arg.groupId)
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

    abstract fun createFragment(): Fragment
    abstract fun init(bean: T?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) {
            val editingId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
            DataStore.editingId = editingId
            if (editingId == 0L) {
                val editingGroup = intent.getLongExtra(EXTRA_GROUP_ID, 0L)
                DataStore.editingGroup = editingGroup
                init(null)
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
                .replace(R.id.settings, createFragment())
                .commit()
        }

    }

    private val child by lazy { supportFragmentManager.findFragmentById(R.id.settings) as MyPreferenceFragmentCompat<T, ProfileSettingsActivity<T>> }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = child.onOptionsItemSelected(item)

    override fun onBackPressed() {
        if (child.unsaved) UnsavedChangesDialogFragment().show(child, REQUEST_UNSAVED_CHANGES)
        else super.onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    abstract class MyPreferenceFragmentCompat<T : AbstractBean, P : ProfileSettingsActivity<T>> :
        PreferenceFragmentCompat() {
        val activity get() = requireActivity() as P
        var unsaved = false
    }

    object PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {
        override fun provideSummary(preference: EditTextPreference?) =
            "\u2022".repeat(preference?.text?.length ?: 0)
    }

}
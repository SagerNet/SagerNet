/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceDataStore
import com.takisoft.preferencex.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ui.ProfileSelectActivity
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.widget.ListListener
import io.nekohasekai.sagernet.widget.TaskerProfilePreference

class TaskerActivity : ThemedActivity(R.layout.layout_config_settings),
    OnPreferenceDataStoreChangeListener {

    val settings by lazy { TaskerBundle.fromIntent(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.tasker_settings)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, MyPreferenceFragmentCompat().also { it.activity = this })
            .commit()

        DataStore.dirty = false
        DataStore.profileCacheStore.registerChangeListener(this)
    }

    lateinit var profile: TaskerProfilePreference

    fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.tasker_preferences)
        profile = findPreference(Key.TASKER_PROFILE)!!
        profile.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == "1") {
                selectProfileForTasker.launch(Intent(
                    this@TaskerActivity, ProfileSelectActivity::class.java
                ).apply {
                    putExtra(ProfileSelectActivity.EXTRA_SELECTED, settings.profileId)
                })
            }
            true
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key != Key.PROFILE_DIRTY) {
            DataStore.dirty = true
        }
        when (key) {
            Key.TASKER_ACTION -> {
                settings.action = DataStore.taskerAction
                profile.isEnabled = settings.action == TaskerBundle.ACTION_START
            }
            Key.TASKER_PROFILE -> {
                if (DataStore.taskerProfile == 0) DataStore.taskerProfileId = -1L
            }
            Key.TASKER_PROFILE_ID -> {
                settings.profileId = DataStore.taskerProfileId
                if (settings.profileId > 0L) runOnMainDispatcher {
                    profile.summaryProvider = profile.summaryProvider
                }
            }
        }
    }

    val selectProfileForTasker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { (resultCode, data) ->
        if (resultCode == Activity.RESULT_OK) {
            val profileId = data!!.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, -1L)
            runOnDefaultDispatcher {
                DataStore.taskerProfileId = profileId
            }
        }
    }

    fun needSave(): Boolean {
        if (!DataStore.dirty) return false
        return true
    }

    fun saveAndExit() {
        setResult(RESULT_OK, settings.toIntent())
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_apply_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_apply -> {
            runOnDefaultDispatcher {
                saveAndExit()
            }
            true
        }
        else -> false
    }

    override fun onBackPressed() {
        if (needSave()) saveAndExit() else super.onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onDestroy()
    }

    class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {

        lateinit var activity: TaskerActivity

        override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            activity.apply {
                createPreferences(savedInstanceState, rootKey)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            ViewCompat.setOnApplyWindowInsetsListener(listView, ListListener)
        }

    }

}
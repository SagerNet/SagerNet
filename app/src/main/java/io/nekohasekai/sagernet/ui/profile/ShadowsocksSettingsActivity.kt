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

package io.nekohasekai.sagernet.ui.profile

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.github.shadowsocks.plugin.*
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.github.shadowsocks.preference.PluginConfigurationDialogFragment
import com.github.shadowsocks.preference.PluginPreference
import com.github.shadowsocks.preference.PluginPreferenceDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.takisoft.preferencex.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>(),
    Preference.OnPreferenceChangeListener {

    override fun createEntity() = ShadowsocksBean()

    private lateinit var plugin: PluginPreference
    private lateinit var pluginConfigure: EditTextPreference
    private lateinit var pluginConfiguration: PluginConfiguration
    private lateinit var receiver: BroadcastReceiver

    override fun ShadowsocksBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverMethod = method
        DataStore.serverPassword = password
        DataStore.serverPlugin = plugin
    }

    override fun ShadowsocksBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        method = DataStore.serverMethod
        password = DataStore.serverPassword
        plugin = DataStore.serverPlugin

    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        receiver = listenForPackageChanges(false) {
            lifecycleScope.launch(Dispatchers.Main) {   // wait until changes were flushed
                whenCreated { initPlugins() }
            }
        }
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

        plugin = findPreference(Key.SERVER_PLUGIN)!!
        pluginConfigure = findPreference(Key.SERVER_PLUGIN_CONFIGURE)!!
        pluginConfigure.setOnBindEditTextListener(EditTextPreferenceModifiers.Monospace)
        pluginConfigure.onPreferenceChangeListener = this@ShadowsocksSettingsActivity
        pluginConfiguration = PluginConfiguration(DataStore.serverPlugin ?: "")
        initPlugins()
    }

    override fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
        setFragmentResultListener(PluginPreferenceDialogFragment::class.java.name) { _, bundle ->
            val selected = plugin.plugins.lookup.getValue(
                bundle.getString(PluginPreferenceDialogFragment.KEY_SELECTED_ID)!!)
            val override = pluginConfiguration.pluginsOptions.keys.firstOrNull {
                plugin.plugins.lookup[it] == selected
            }
            pluginConfiguration =
                PluginConfiguration(pluginConfiguration.pluginsOptions, override ?: selected.id)
            DataStore.serverPlugin = pluginConfiguration.toString()
            DataStore.dirty = true
            plugin.value = pluginConfiguration.selected
            pluginConfigure.isEnabled = selected !is NoPlugin
            pluginConfigure.text = pluginConfiguration.getOptions().toString()
            if (!selected.trusted) {
                Snackbar.make(requireView(), R.string.plugin_untrusted, Snackbar.LENGTH_LONG).show()
            }
        }
        AlertDialogFragment.setResultListener<Empty>(this,
            UnsavedChangesDialogFragment::class.java.simpleName) { which, _ ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    runOnDefaultDispatcher {
                        saveAndExit()
                    }
                }
                DialogInterface.BUTTON_NEGATIVE -> requireActivity().finish()
            }
        }
    }

    private fun initPlugins() {
        plugin.value = pluginConfiguration.selected
        plugin.init()
        pluginConfigure.isEnabled = plugin.selectedEntry?.let { it is NoPlugin } == false
        pluginConfigure.text = pluginConfiguration.getOptions().toString()
    }

    private fun showPluginEditor() {
        PluginConfigurationDialogFragment().apply {
            setArg(Key.SERVER_PLUGIN_CONFIGURE, pluginConfiguration.selected)
            setTargetFragment(child, 0)
        }.showAllowingStateLoss(supportFragmentManager, Key.SERVER_PLUGIN_CONFIGURE)
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean = try {
        val selected = pluginConfiguration.selected
        pluginConfiguration = PluginConfiguration((pluginConfiguration.pluginsOptions +
                (pluginConfiguration.selected to PluginOptions(selected,
                    newValue as? String?))).toMutableMap(),
            selected)
        DataStore.serverPlugin = pluginConfiguration.toString()
        DataStore.dirty = true
        true
    } catch (exc: RuntimeException) {
        Snackbar.make(child.requireView(), exc.readableMessage, Snackbar.LENGTH_LONG).show()
        false
    }

    private val configurePlugin =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { (resultCode, data) ->
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val options = data?.getStringExtra(PluginContract.EXTRA_OPTIONS)
                    pluginConfigure.text = options
                    onPreferenceChange(null, options)
                }
                PluginContract.RESULT_FALLBACK -> showPluginEditor()
            }
        }

    override fun PreferenceFragmentCompat.displayPreferenceDialog(preference: Preference): Boolean {
        when (preference.key) {
            Key.SERVER_PLUGIN -> PluginPreferenceDialogFragment().apply {
                setArg(Key.SERVER_PLUGIN)
                setTargetFragment(child, 0)
            }.showAllowingStateLoss(supportFragmentManager, Key.SERVER_PLUGIN)
            Key.SERVER_PLUGIN_CONFIGURE -> {
                val intent = PluginManager.buildIntent(plugin.selectedEntry!!.id,
                    PluginContract.ACTION_CONFIGURE)
                if (intent.resolveActivity(packageManager) == null) showPluginEditor() else {
                    configurePlugin.launch(intent
                        .putExtra(PluginContract.EXTRA_OPTIONS,
                            pluginConfiguration.getOptions().toString()))
                }
            }
            else -> return false
        }
        return true
    }

    val pluginHelp = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { (resultCode, data) ->
        if (resultCode == Activity.RESULT_OK) MaterialAlertDialogBuilder(this)
            .setTitle("?")
            .setMessage(data?.getCharSequenceExtra(PluginContract.EXTRA_HELP_MESSAGE))
            .show()
    }

}
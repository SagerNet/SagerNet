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

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.addOverScrollListener
import io.nekohasekai.sagernet.ktx.remove
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var listener: (BaseService.State) -> Unit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addOverScrollListener(listView)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.global_preferences)
        val persistAcrossReboot = findPreference<SwitchPreference>(Key.PERSIST_ACROSS_REBOOT)!!
        val directBootAware = findPreference<SwitchPreference>(Key.DIRECT_BOOT_AWARE)!!
        val portSocks5 = findPreference<EditTextPreference>(Key.SOCKS_PORT)!!
        val speedInterval = findPreference<Preference>(Key.SPEED_INTERVAL)!!
        val serviceMode = findPreference<Preference>(Key.SERVICE_MODE)!!
        val allowAccess = findPreference<Preference>(Key.ALLOW_ACCESS)!!
        val requireHttp = findPreference<SwitchPreference>(Key.REQUIRE_HTTP)!!
        val portHttp = findPreference<EditTextPreference>(Key.HTTP_PORT)!!
        val showStopButton = findPreference<SwitchPreference>(Key.SHOW_STOP_BUTTON)!!
        if (Build.VERSION.SDK_INT < 24) {
            showStopButton.isVisible = false
        }
        val securityAdvisory = findPreference<SwitchPreference>(Key.SECURITY_ADVISORY)!!
        val showDirectSpeed = findPreference<SwitchPreference>(Key.SHOW_DIRECT_SPEED)!!

        portSocks5.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        portHttp.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

        val currServiceMode = DataStore.serviceMode
        val metedNetwork = findPreference<Preference>(Key.METERED_NETWORK)!!
        if (Build.VERSION.SDK_INT >= 28) {
            metedNetwork.isEnabled = currServiceMode == Key.MODE_VPN
        } else {
            metedNetwork.remove()
        }

        listener = {
            val stopped = it == BaseService.State.Stopped
            val sMode = DataStore.serviceMode

            runOnMainDispatcher {
                persistAcrossReboot.isEnabled = stopped
                directBootAware.isEnabled = stopped
                serviceMode.isEnabled = stopped
                speedInterval.isEnabled = stopped
                portSocks5.isEnabled = stopped
                requireHttp.isEnabled = stopped
                portHttp.isEnabled = stopped
                showStopButton.isEnabled = stopped
                securityAdvisory.isEnabled = stopped
                showDirectSpeed.isEnabled = stopped

                metedNetwork.isEnabled = sMode == Key.MODE_VPN && stopped

                allowAccess.isEnabled = stopped
            }
        }

    }

    override fun onResume() {
        super.onResume()

        if (::listener.isInitialized) {
            MainActivity.stateListener = listener
            listener((activity as MainActivity).state)
        }
    }

    override fun onDestroy() {
        if (MainActivity.stateListener == listener) {
            MainActivity.stateListener = null
        }
        super.onDestroy()
    }
}
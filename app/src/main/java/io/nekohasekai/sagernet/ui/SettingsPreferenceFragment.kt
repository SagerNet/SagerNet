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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.remove
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore

        runOnDefaultDispatcher {
            DataStore.initGlobal()
            onMainDispatcher {
                addPreferencesFromResource(R.xml.global_preferences)
            }
            val persistAcrossReboot = findPreference<SwitchPreference>(Key.PERSIST_ACROSS_REBOOT)!!
            val directBootAware = findPreference<SwitchPreference>(Key.DIRECT_BOOT_AWARE)!!
            val portSocks5 = findPreference<EditTextPreference>(Key.SOCKS_PORT)!!

            val serviceMode = findPreference<Preference>(Key.SERVICE_MODE)!!
            val routeMode = findPreference<Preference>(Key.ROUTE_MODE)!!
            val allowAccess = findPreference<Preference>(Key.ALLOW_ACCESS)!!
            val ipv6Route = findPreference<Preference>(Key.IPV6_ROUTE)!!
            val preferIpv6 = findPreference<Preference>(Key.PREFER_IPV6)!!

            val requireHttp = findPreference<SwitchPreference>(Key.REQUIRE_HTTP)!!
            val portHttp = findPreference<EditTextPreference>(Key.HTTP_PORT)!!
            val forceShadowsocksRust =
                findPreference<SwitchPreference>(Key.FORCE_SHADOWSOCKS_RUST)!!

            val remoteDns = findPreference<Preference>(Key.REMOTE_DNS)!!
            val enableLocalDns = findPreference<SwitchPreference>(Key.ENABLE_LOCAL_DNS)!!
            val portLocalDns = findPreference<EditTextPreference>(Key.LOCAL_DNS_PORT)!!
            val domesticDns = findPreference<EditTextPreference>(Key.DOMESTIC_DNS)!!

            portSocks5.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
            portLocalDns.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
            portHttp.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

            val currServiceMode = DataStore.serviceMode
            isProxyApps = findPreference(Key.PROXY_APPS)!!
            isProxyApps.isEnabled = currServiceMode == Key.MODE_VPN
            isProxyApps.setOnPreferenceChangeListener { _, newValue ->
                startActivity(Intent(activity, AppManagerActivity::class.java))
                if (newValue as Boolean) DataStore.dirty = true
                newValue
            }
            val metedNetwork = findPreference<Preference>(Key.METERED_NETWORK)!!
            if (Build.VERSION.SDK_INT >= 28) {
                metedNetwork.isEnabled = currServiceMode == Key.MODE_VPN
            } else {
                metedNetwork.remove()
            }

            val listener: (BaseService.State) -> Unit = {
                val stopped = it == BaseService.State.Stopped
                val sMode = DataStore.serviceMode

                listView.post {
                    persistAcrossReboot.isEnabled = stopped
                    directBootAware.isEnabled = stopped
                    serviceMode.isEnabled = stopped
                    portSocks5.isEnabled = stopped
                    requireHttp.isEnabled = stopped
                    portHttp.isEnabled = stopped
                    forceShadowsocksRust.isEnabled = stopped

                    isProxyApps.isEnabled = sMode == Key.MODE_VPN && stopped
                    metedNetwork.isEnabled = sMode == Key.MODE_VPN && stopped

                    routeMode.isEnabled = stopped
                    allowAccess.isEnabled = stopped
                    remoteDns.isEnabled = stopped
                    enableLocalDns.isEnabled = stopped
                    portLocalDns.isEnabled = stopped
                    domesticDns.isEnabled = stopped
                    ipv6Route.isEnabled = stopped
                    preferIpv6.isEnabled = stopped
                }
            }

            listener((activity as MainActivity).state)
            MainActivity.stateListener = listener
            serviceMode.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    listView.post {
                        isProxyApps.isEnabled = newValue == Key.MODE_VPN
                    }
                    true
                }
        }

    }

    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps // fetch proxyApps updated by AppManager
        }
    }

    override fun onDestroy() {
        MainActivity.stateListener = null
        super.onDestroy()
    }
}
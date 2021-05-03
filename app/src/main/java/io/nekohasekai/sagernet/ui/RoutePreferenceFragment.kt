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
import android.os.Bundle
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.addOverScrollListener
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher

class RoutePreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference
    private lateinit var listener: (BaseService.State) -> Unit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addOverScrollListener(listView)
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        addPreferencesFromResource(R.xml.route_preferences)

        val ipv6Route = findPreference<Preference>(Key.IPV6_ROUTE)!!
        val preferIpv6 = findPreference<Preference>(Key.PREFER_IPV6)!!
        val domainStrategy = findPreference<Preference>(Key.DOMAIN_STRATEGY)!!
        val domainMatcher = findPreference<Preference>(Key.DOMAIN_MATCHER)!!
        val trafficSniffing = findPreference<Preference>(Key.TRAFFIC_SNIFFING)!!
        val enableMux = findPreference<Preference>(Key.ENABLE_MUX)!!
        val muxConcurrency = findPreference<EditTextPreference>(Key.MUX_CONCURRENCY)!!

        val bypassLan = findPreference<Preference>(Key.BYPASS_LAN)!!
        val routeChina = findPreference<Preference>(Key.ROUTE_CHINA)!!
        val blockAds = findPreference<Preference>(Key.BLOCK_ADS)!!

        val forceShadowsocksRust =
            findPreference<SwitchPreference>(Key.FORCE_SHADOWSOCKS_RUST)!!

        val remoteDns = findPreference<Preference>(Key.REMOTE_DNS)!!
        val enableLocalDns = findPreference<SwitchPreference>(Key.ENABLE_LOCAL_DNS)!!
        val portLocalDns = findPreference<EditTextPreference>(Key.LOCAL_DNS_PORT)!!
        val domesticDns = findPreference<EditTextPreference>(Key.DOMESTIC_DNS)!!

        portLocalDns.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        muxConcurrency.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

        val currServiceMode = DataStore.serviceMode
        isProxyApps = findPreference(Key.PROXY_APPS)!!
        isProxyApps.isEnabled = currServiceMode == Key.MODE_VPN
        isProxyApps.setOnPreferenceChangeListener { _, newValue ->
            startActivity(Intent(activity, AppManagerActivity::class.java))
            if (newValue as Boolean) DataStore.dirty = true
            newValue
        }

        listener = {
            val stopped = it == BaseService.State.Stopped
            val sMode = DataStore.serviceMode

            runOnMainDispatcher {
                domainStrategy.isEnabled = stopped
                domainMatcher.isEnabled = stopped
                trafficSniffing.isEnabled = stopped
                enableMux.isEnabled = stopped
                muxConcurrency.isEnabled = stopped

                bypassLan.isEnabled = stopped
                blockAds.isEnabled = stopped
                routeChina.isEnabled = stopped

                forceShadowsocksRust.isEnabled = stopped

                isProxyApps.isEnabled = sMode == Key.MODE_VPN && stopped

                remoteDns.isEnabled = stopped
                enableLocalDns.isEnabled = stopped
                portLocalDns.isEnabled = stopped
                domesticDns.isEnabled = stopped
                ipv6Route.isEnabled = stopped
                preferIpv6.isEnabled = stopped
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (::listener.isInitialized) {
            MainActivity.stateListener = listener
            listener((activity as MainActivity).state)
        }
        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
    }

    override fun onDestroy() {
        if (MainActivity.stateListener == listener) {
            MainActivity.stateListener = null
        }
        super.onDestroy()
    }
}
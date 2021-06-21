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
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.DnsMode
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.ColorPickerPreference

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.layoutManager = FixedLinearLayoutManager(listView)
    }

    val reloadListener = Preference.OnPreferenceChangeListener { _, _ ->
        needReload()
        true
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.global_preferences)
        val appTheme = findPreference<ColorPickerPreference>(Key.APP_THEME)!!
        if (!isExpert) {
            appTheme.remove()
        } else {
            appTheme.setOnPreferenceChangeListener { _, newTheme ->
                if (serviceStarted()) {
                    SagerNet.reloadService()
                }
                val theme = Theme.getTheme(newTheme as Int)
                app.setTheme(theme)
                requireActivity().apply {
                    setTheme(theme)
                    ActivityCompat.recreate(this)
                }
                true
            }
        }
        val nightTheme = findPreference<SimpleMenuPreference>(Key.NIGHT_THEME)!!
        nightTheme.setOnPreferenceChangeListener { _, newTheme ->
            Theme.currentNightMode = (newTheme as String).toInt()
            Theme.applyNightTheme()
            true
        }
        val portSocks5 = findPreference<EditTextPreference>(Key.SOCKS_PORT)!!
        val speedInterval = findPreference<Preference>(Key.SPEED_INTERVAL)!!
        val serviceMode = findPreference<Preference>(Key.SERVICE_MODE)!!
        val allowAccess = findPreference<Preference>(Key.ALLOW_ACCESS)!!
        val requireHttp = findPreference<SwitchPreference>(Key.REQUIRE_HTTP)!!
        val portHttp = findPreference<EditTextPreference>(Key.HTTP_PORT)!!
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            requireHttp.remove()
            portHttp.setIcon(R.drawable.ic_baseline_http_24)
            portHttp.onPreferenceChangeListener = reloadListener
        } else {
            portHttp.isEnabled = requireHttp.isChecked
            requireHttp.setOnPreferenceChangeListener { _, newValue ->
                portHttp.isEnabled = newValue as Boolean
                needReload()
                true
            }
        }


        val showStopButton = findPreference<SwitchPreference>(Key.SHOW_STOP_BUTTON)!!
        if (Build.VERSION.SDK_INT < 24) {
            showStopButton.remove()
        }
        val showDirectSpeed = findPreference<SwitchPreference>(Key.SHOW_DIRECT_SPEED)!!
        val ipv6Mode = findPreference<Preference>(Key.IPV6_MODE)!!
        val domainStrategy = findPreference<Preference>(Key.DOMAIN_STRATEGY)!!
        val domainMatcher = findPreference<Preference>(Key.DOMAIN_MATCHER)!!
        if (!isExpert) {
            domainMatcher.remove()
        }

        val trafficSniffing = findPreference<Preference>(Key.TRAFFIC_SNIFFING)!!
        val enableMux = findPreference<Preference>(Key.ENABLE_MUX)!!
        val enableMuxForAll = findPreference<Preference>(Key.ENABLE_MUX_FOR_ALL)!!
        val muxConcurrency = findPreference<EditTextPreference>(Key.MUX_CONCURRENCY)!!
        val tcpKeepAliveInterval = findPreference<EditTextPreference>(Key.TCP_KEEP_ALIVE_INTERVAL)!!

        val bypassLan = findPreference<Preference>(Key.BYPASS_LAN)!!

        val forceShadowsocksRust = findPreference<SwitchPreference>(Key.FORCE_SHADOWSOCKS_RUST)!!
        if (!isExpert) {
            forceShadowsocksRust.remove()
        }

        val dnsMode = findPreference<SimpleMenuPreference>(Key.DNS_MODE)!!
        val systemDns = findPreference<EditTextPreference>(Key.SYSTEM_DNS)!!
        val localDns = findPreference<EditTextPreference>(Key.LOCAL_DNS)!!
        val enableDomesticDns = findPreference<SwitchPreference>(Key.ENABLE_DOMESTIC_DNS)!!
        val domesticDns = findPreference<EditTextPreference>(Key.DOMESTIC_DNS)!!

        val requireTransproxy = findPreference<SwitchPreference>(Key.REQUIRE_TRANSPROXY)!!
        val transproxyPort = findPreference<EditTextPreference>(Key.TRANSPROXY_PORT)!!
        val transproxyMode = findPreference<SimpleMenuPreference>(Key.TRANSPROXY_MODE)!!

        transproxyPort.isEnabled = requireTransproxy.isChecked
        transproxyMode.isEnabled = requireTransproxy.isChecked

        requireTransproxy.setOnPreferenceChangeListener { _, newValue ->
            transproxyPort.isEnabled = newValue as Boolean
            transproxyMode.isEnabled = newValue
            needReload()
            true
        }

        fun updateDnsMode(newMode: Int) {
            systemDns.isVisible = newMode == DnsMode.SYSTEM
            val useLocalDns = newMode in intArrayOf(DnsMode.LOCAL, DnsMode.FAKEDNS_LOCAL)
            localDns.isVisible = useLocalDns
            enableDomesticDns.isVisible = useLocalDns
            domesticDns.isVisible = useLocalDns
            domesticDns.isEnabled = useLocalDns && enableDomesticDns.isChecked
        }
        updateDnsMode(DataStore.dnsMode)
        enableDomesticDns.setOnPreferenceChangeListener { _, newValue ->
            domesticDns.isEnabled = newValue as Boolean
            needReload()
            true
        }
        dnsMode.setOnPreferenceChangeListener { _, newValue ->
            updateDnsMode((newValue as String).toInt())
            needReload()
            true
        }

        val portLocalDns = findPreference<EditTextPreference>(Key.LOCAL_DNS_PORT)!!

        portLocalDns.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        muxConcurrency.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        portSocks5.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        portHttp.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

        val metedNetwork = findPreference<Preference>(Key.METERED_NETWORK)!!
        if (Build.VERSION.SDK_INT < 28) {
            metedNetwork.remove()
        }
        isProxyApps = findPreference(Key.PROXY_APPS)!!
        isProxyApps.setOnPreferenceChangeListener { _, newValue ->
            startActivity(Intent(activity, AppManagerActivity::class.java))
            if (newValue as Boolean) DataStore.dirty = true
            newValue
        }

        serviceMode.onPreferenceChangeListener = reloadListener
        speedInterval.onPreferenceChangeListener = reloadListener
        portSocks5.onPreferenceChangeListener = reloadListener
        portHttp.onPreferenceChangeListener = reloadListener
        showStopButton.onPreferenceChangeListener = reloadListener
        showDirectSpeed.onPreferenceChangeListener = reloadListener
        domainStrategy.onPreferenceChangeListener = reloadListener
        domainMatcher.onPreferenceChangeListener = reloadListener
        trafficSniffing.onPreferenceChangeListener = reloadListener
        enableMux.onPreferenceChangeListener = reloadListener
        enableMuxForAll.onPreferenceChangeListener = reloadListener
        muxConcurrency.onPreferenceChangeListener = reloadListener
        tcpKeepAliveInterval.onPreferenceChangeListener = reloadListener
        bypassLan.onPreferenceChangeListener = reloadListener
        forceShadowsocksRust.onPreferenceChangeListener = reloadListener

        systemDns.onPreferenceChangeListener = reloadListener
        localDns.onPreferenceChangeListener = reloadListener
        domesticDns.onPreferenceChangeListener = reloadListener

        portLocalDns.onPreferenceChangeListener = reloadListener
        ipv6Mode.onPreferenceChangeListener = reloadListener
        allowAccess.onPreferenceChangeListener = reloadListener

        transproxyPort.onPreferenceChangeListener = reloadListener
        transproxyMode.onPreferenceChangeListener = reloadListener
    }

    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
    }

}
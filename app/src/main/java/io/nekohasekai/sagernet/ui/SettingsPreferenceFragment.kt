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
import io.nekohasekai.sagernet.*
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
        val appendHttpProxy = findPreference<SwitchPreference>(Key.APPEND_HTTP_PROXY)!!
        val portHttp = findPreference<EditTextPreference>(Key.HTTP_PORT)!!
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> {
                requireHttp.remove()
                appendHttpProxy.remove()
                portHttp.setIcon(R.drawable.ic_baseline_http_24)
                portHttp.onPreferenceChangeListener = reloadListener
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                portHttp.isEnabled = requireHttp.isChecked
                appendHttpProxy.remove()
                requireHttp.setOnPreferenceChangeListener { _, newValue ->
                    portHttp.isEnabled = newValue as Boolean
                    needReload()
                    true
                }
            }
            else -> {
                portHttp.isEnabled = requireHttp.isChecked
                appendHttpProxy.isEnabled = requireHttp.isChecked
                requireHttp.setOnPreferenceChangeListener { _, newValue ->
                    portHttp.isEnabled = newValue as Boolean
                    appendHttpProxy.isEnabled = newValue as Boolean
                    needReload()
                    true
                }
            }
        }

        val portLocalDns = findPreference<EditTextPreference>(Key.LOCAL_DNS_PORT)!!

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

        val bypassLan = findPreference<SwitchPreference>(Key.BYPASS_LAN)!!
        val bypassLanInCoreOnly = findPreference<SwitchPreference>(Key.BYPASS_LAN_IN_CORE_ONLY)!!

        bypassLanInCoreOnly.isEnabled = bypassLan.isChecked
        bypassLan.setOnPreferenceChangeListener { _, newValue ->
            bypassLanInCoreOnly.isEnabled = newValue as Boolean
            needReload()
            true
        }

        val remoteDns = findPreference<EditTextPreference>(Key.REMOTE_DNS)!!
        val directDns = findPreference<EditTextPreference>(Key.DIRECT_DNS)!!
        val enableDnsRouting = findPreference<SwitchPreference>(Key.ENABLE_DNS_ROUTING)!!
        val enableFakeDns = findPreference<SwitchPreference>(Key.ENABLE_FAKEDNS)!!

        val requireTransproxy = findPreference<SwitchPreference>(Key.REQUIRE_TRANSPROXY)!!
        val transproxyPort = findPreference<EditTextPreference>(Key.TRANSPROXY_PORT)!!
        val transproxyMode = findPreference<SimpleMenuPreference>(Key.TRANSPROXY_MODE)!!
        val enableLog = findPreference<SwitchPreference>(Key.ENABLE_LOG)!!

        val apiPort = findPreference<EditTextPreference>(Key.API_PORT)!!
        val probeIndival = findPreference<EditTextPreference>(Key.PROBE_INTERVAL)!!

        transproxyPort.isEnabled = requireTransproxy.isChecked
        transproxyMode.isEnabled = requireTransproxy.isChecked

        requireTransproxy.setOnPreferenceChangeListener { _, newValue ->
            transproxyPort.isEnabled = newValue as Boolean
            transproxyMode.isEnabled = newValue
            needReload()
            true
        }

        val vpnMode = findPreference<SimpleMenuPreference>(Key.VPN_MODE)!!
        val multiThreadForward = findPreference<SwitchPreference>(Key.MULTI_THREAD_FORWARD)!!
        val icmpEchoStrategy = findPreference<SimpleMenuPreference>(Key.ICMP_ECHO_STRATEGY)!!
        val icmpEchoReplyDelay = findPreference<EditTextPreference>(Key.ICMP_ECHO_REPLY_DELAY)!!
        val ipOtherStrategy = findPreference<SimpleMenuPreference>(Key.IP_OTHER_STRATEGY)!!

        fun updateVpnMode(newMode: Int) {
            val isForwarding = newMode == VpnMode.EXPERIMENTAL_FORWARDING

            multiThreadForward.isVisible = isForwarding
            icmpEchoStrategy.isVisible = isForwarding
            icmpEchoReplyDelay.isVisible = isForwarding
            if (isForwarding) {
                icmpEchoReplyDelay.isEnabled = icmpEchoStrategy.value == "${PacketStrategy.REPLY}"
            }
            ipOtherStrategy.isVisible = isForwarding
        }

        updateVpnMode(DataStore.vpnMode)
        vpnMode.setOnPreferenceChangeListener { _, newValue ->
            updateVpnMode((newValue as String).toInt())
            needReload()
            true
        }
        icmpEchoStrategy.setOnPreferenceChangeListener { _, newValue ->
            icmpEchoReplyDelay.isEnabled = newValue == "${PacketStrategy.REPLY}"
            needReload()
            true
        }

        val providerTrojan = findPreference<SimpleMenuPreference>(Key.PROVIDER_TROJAN)!!
        val providerShadowsocksAEAD = findPreference<SimpleMenuPreference>(Key.PROVIDER_SS_AEAD)!!

        if (!isExpert) {
            providerTrojan.setEntries(R.array.trojan_provider)
            providerTrojan.setEntryValues(R.array.trojan_provider_value)
        }

        val dnsHosts = findPreference<EditTextPreference>(Key.DNS_HOSTS)!!

        portLocalDns.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        muxConcurrency.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        portSocks5.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        portHttp.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        apiPort.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        dnsHosts.setOnBindEditTextListener(EditTextPreferenceModifiers.Hosts)

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
        appendHttpProxy.onPreferenceChangeListener = reloadListener
        showStopButton.onPreferenceChangeListener = reloadListener
        showDirectSpeed.onPreferenceChangeListener = reloadListener
        domainStrategy.onPreferenceChangeListener = reloadListener
        domainMatcher.onPreferenceChangeListener = reloadListener
        trafficSniffing.onPreferenceChangeListener = reloadListener
        enableMux.onPreferenceChangeListener = reloadListener
        enableMuxForAll.onPreferenceChangeListener = reloadListener
        muxConcurrency.onPreferenceChangeListener = reloadListener
        tcpKeepAliveInterval.onPreferenceChangeListener = reloadListener
        bypassLanInCoreOnly.onPreferenceChangeListener = reloadListener

        remoteDns.onPreferenceChangeListener = reloadListener
        directDns.onPreferenceChangeListener = reloadListener
        enableDnsRouting.onPreferenceChangeListener = reloadListener
        enableFakeDns.onPreferenceChangeListener = reloadListener
        dnsHosts.onPreferenceChangeListener = reloadListener

        portLocalDns.onPreferenceChangeListener = reloadListener
        ipv6Mode.onPreferenceChangeListener = reloadListener
        allowAccess.onPreferenceChangeListener = reloadListener

        transproxyPort.onPreferenceChangeListener = reloadListener
        transproxyMode.onPreferenceChangeListener = reloadListener

        enableLog.onPreferenceChangeListener = reloadListener

        probeIndival.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        probeIndival.onPreferenceChangeListener = reloadListener

        multiThreadForward.onPreferenceChangeListener = reloadListener
        icmpEchoReplyDelay.onPreferenceChangeListener = reloadListener
        icmpEchoReplyDelay.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        ipOtherStrategy.onPreferenceChangeListener = reloadListener

        providerTrojan.onPreferenceChangeListener = reloadListener
        providerShadowsocksAEAD.onPreferenceChangeListener = reloadListener

    }

    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
    }

}
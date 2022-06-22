/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.*
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.ColorPickerPreference
import kotlinx.coroutines.delay
import libcore.Libcore
import rikka.shizuku.Shizuku
import java.io.File

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
                if (SagerNet.started) {
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

        portHttp.isEnabled = requireHttp.isChecked
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            appendHttpProxy.remove()
            requireHttp.setOnPreferenceChangeListener { _, newValue ->
                portHttp.isEnabled = newValue as Boolean
                needReload()
                true
            }
        } else {
            appendHttpProxy.isEnabled = requireHttp.isChecked
            requireHttp.setOnPreferenceChangeListener { _, newValue ->
                portHttp.isEnabled = newValue as Boolean
                appendHttpProxy.isEnabled = newValue as Boolean
                needReload()
                true
            }
        }

        val portLocalDns = findPreference<EditTextPreference>(Key.LOCAL_DNS_PORT)!!
        val showDirectSpeed = findPreference<SwitchPreference>(Key.SHOW_DIRECT_SPEED)!!
        val ipv6Mode = findPreference<Preference>(Key.IPV6_MODE)!!
        val domainStrategy = findPreference<Preference>(Key.DOMAIN_STRATEGY)!!
        val trafficSniffing = findPreference<Preference>(Key.TRAFFIC_SNIFFING)!!
        val enableMux = findPreference<Preference>(Key.ENABLE_MUX)!!
        val enableMuxForAll = findPreference<Preference>(Key.ENABLE_MUX_FOR_ALL)!!
        enableMuxForAll.isEnabled = DataStore.enableMux

        enableMux.setOnPreferenceChangeListener { _, newValue ->
            enableMuxForAll.isEnabled = newValue as Boolean
            needReload()
            true
        }

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
        val useLocalDnsAsDirectDns = findPreference<SwitchPreference>(Key.USE_LOCAL_DNS_AS_DIRECT_DNS)!!

        directDns.isEnabled = !DataStore.useLocalDnsAsDirectDns
        useLocalDnsAsDirectDns.setOnPreferenceChangeListener { _, newValue ->
            directDns.isEnabled = newValue == false
            needReload()
            true
        }

        val enableDnsRouting = findPreference<SwitchPreference>(Key.ENABLE_DNS_ROUTING)!!
        val disableDnsExpire = findPreference<SwitchPreference>(Key.DISABLE_DNS_EXPIRE)!!

        val requireTransproxy = findPreference<SwitchPreference>(Key.REQUIRE_TRANSPROXY)!!
        val transproxyPort = findPreference<EditTextPreference>(Key.TRANSPROXY_PORT)!!
        val transproxyMode = findPreference<SimpleMenuPreference>(Key.TRANSPROXY_MODE)!!
        val enableLog = findPreference<SwitchPreference>(Key.ENABLE_LOG)!!

        transproxyPort.isEnabled = requireTransproxy.isChecked
        transproxyMode.isEnabled = requireTransproxy.isChecked

        requireTransproxy.setOnPreferenceChangeListener { _, newValue ->
            transproxyPort.isEnabled = newValue as Boolean
            transproxyMode.isEnabled = newValue
            needReload()
            true
        }

        val providerTrojan = findPreference<SimpleMenuPreference>(Key.PROVIDER_TROJAN)!!
        val dnsHosts = findPreference<EditTextPreference>(Key.DNS_HOSTS)!!

        portLocalDns.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        muxConcurrency.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        portSocks5.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        portHttp.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
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

        val appTrafficStatistics = findPreference<SwitchPreference>(Key.APP_TRAFFIC_STATISTICS)!!
        val profileTrafficStatistics = findPreference<SwitchPreference>(Key.PROFILE_TRAFFIC_STATISTICS)!!
        speedInterval.isEnabled = profileTrafficStatistics.isChecked
        profileTrafficStatistics.setOnPreferenceChangeListener { _, newValue ->
            speedInterval.isEnabled = newValue as Boolean
            needReload()
            true
        }

        serviceMode.setOnPreferenceChangeListener { _, _ ->
            if (SagerNet.started) {
                SagerNet.stopService()
                runOnMainDispatcher {
                    delay(300)
                    SagerNet.startService()
                }
            }

            true
        }

        val tunImplementation = findPreference<SimpleMenuPreference>(Key.TUN_IMPLEMENTATION)!!
        val destinationOverride = findPreference<SwitchPreference>(Key.DESTINATION_OVERRIDE)!!
        val resolveDestination = findPreference<SwitchPreference>(Key.RESOLVE_DESTINATION)!!
        val enablePcap = findPreference<SwitchPreference>(Key.ENABLE_PCAP)!!
        val providerRootCA = findPreference<SimpleMenuPreference>(Key.PROVIDER_ROOT_CA)!!

        providerRootCA.setOnPreferenceChangeListener { _, newValue ->
            val useSystem = (newValue as String) == "${RootCAProvider.SYSTEM}"
            Libcore.updateSystemRoots(useSystem)
            (requireActivity() as? MainActivity)?.connection?.service?.updateSystemRoots(useSystem)
            needReload()
            true
        }

        val mtu = findPreference<EditTextPreference>(Key.MTU)!!
        mtu.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        mtu.isEnabled = !DataStore.useUpstreamInterfaceMTU

        val useUpstreamInterfaceMTU = findPreference<SwitchPreference>(Key.USE_UPSTREAM_INTERFACE_MTU)!!
        useUpstreamInterfaceMTU.setOnPreferenceChangeListener { _, newValue ->
            mtu.isEnabled = !(newValue as Boolean)
            needReload()
            true
        }

        val acquireWakeLock = findPreference<SwitchPreference>(Key.ACQUIRE_WAKE_LOCK)!!

/*        val installerProvider = findPreference<SimpleMenuPreference>(Key.PROVIDER_INSTALLER)!!
        installerProvider.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == "${InstallerProvider.SHIZUKU}") {
                checkShizuku()
            } else {
                true
            }
        }*/

        speedInterval.onPreferenceChangeListener = reloadListener
        portSocks5.onPreferenceChangeListener = reloadListener
        portHttp.onPreferenceChangeListener = reloadListener
        appendHttpProxy.onPreferenceChangeListener = reloadListener
        showDirectSpeed.onPreferenceChangeListener = reloadListener
        domainStrategy.onPreferenceChangeListener = reloadListener
        trafficSniffing.onPreferenceChangeListener = reloadListener
        enableMuxForAll.onPreferenceChangeListener = reloadListener
        muxConcurrency.onPreferenceChangeListener = reloadListener
        tcpKeepAliveInterval.onPreferenceChangeListener = reloadListener
        bypassLanInCoreOnly.onPreferenceChangeListener = reloadListener

        remoteDns.onPreferenceChangeListener = reloadListener
        directDns.onPreferenceChangeListener = reloadListener
        dnsHosts.onPreferenceChangeListener = reloadListener
        enableDnsRouting.onPreferenceChangeListener = reloadListener
        disableDnsExpire.onPreferenceChangeListener = reloadListener

        portLocalDns.onPreferenceChangeListener = reloadListener
        ipv6Mode.onPreferenceChangeListener = reloadListener
        allowAccess.onPreferenceChangeListener = reloadListener

        transproxyPort.onPreferenceChangeListener = reloadListener
        transproxyMode.onPreferenceChangeListener = reloadListener

        enableLog.onPreferenceChangeListener = reloadListener

        providerTrojan.onPreferenceChangeListener = reloadListener
        appTrafficStatistics.onPreferenceChangeListener = reloadListener
        tunImplementation.onPreferenceChangeListener = reloadListener
        destinationOverride.onPreferenceChangeListener = reloadListener
        resolveDestination.onPreferenceChangeListener = reloadListener
        mtu.onPreferenceChangeListener = reloadListener
        acquireWakeLock.onPreferenceChangeListener = reloadListener

        enablePcap.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                val path = File(
                    app.getExternalFilesDir(null)?.apply { mkdirs() } ?: app.filesDir,
                    "pcap"
                ).absolutePath
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.pcap)
                    setMessage(resources.getString(R.string.pcap_notice, path))
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        needReload()
                    }
                    setNegativeButton(android.R.string.copy) { _, _ ->
                        SagerNet.trySetPrimaryClip(path)
                        snackbar(R.string.copy_success).show()
                    }
                }.show()
                if (tunImplementation.value != "${TunImplementation.GVISOR}") {
                    tunImplementation.value = "${TunImplementation.GVISOR}"
                }
            } else needReload()
            true
        }

    }

    private fun checkShizuku(): Boolean {
        val permission = try {
            Shizuku.checkSelfPermission()
        } catch (e: Exception) {
            context?.shizukuError()
            Logs.w(e)
            return false
        }
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
        }
        return true
    }

    private fun Context.shizukuError() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
            .setMessage(R.string.shizuku_unavailable)
            .setPositiveButton(R.string.action_learn_more) { _, _ ->
                launchCustomTab("https://shizuku.rikka.app/")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
    }

}
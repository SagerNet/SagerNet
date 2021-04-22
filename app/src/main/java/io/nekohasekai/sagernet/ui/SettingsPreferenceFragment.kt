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
import io.nekohasekai.sagernet.RouteMode
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

            val remoteDns = findPreference<Preference>(Key.REMOTE_DNS)!!
            val enableLocalDns = findPreference<SwitchPreference>(Key.ENABLE_LOCAL_DNS)!!
            val portLocalDns = findPreference<EditTextPreference>(Key.LOCAL_DNS_PORT)!!
            val domesticDns = findPreference<EditTextPreference>(Key.DOMESTIC_DNS)!!

            portSocks5.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
            portLocalDns.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

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
                persistAcrossReboot.isEnabled = stopped
                directBootAware.isEnabled = stopped
                serviceMode.isEnabled = stopped
                portSocks5.isEnabled = stopped

                isProxyApps.isEnabled = currServiceMode == Key.MODE_VPN && stopped
                metedNetwork.isEnabled = currServiceMode == Key.MODE_VPN && stopped

                routeMode.isEnabled = stopped
                allowAccess.isEnabled = stopped
                remoteDns.isEnabled = stopped
                portLocalDns.isEnabled = stopped
                domesticDns.isEnabled = stopped
                ipv6Route.isEnabled = stopped
                preferIpv6.isEnabled = stopped

                runOnDefaultDispatcher {
                    when (DataStore.routeMode) {
                        RouteMode.BYPASS_CHINA, RouteMode.BYPASS_LAN_CHINA -> {
                            onMainDispatcher {
                                enableLocalDns.isChecked = true
                                enableLocalDns.isEnabled = false
                            }
                        }
                        else -> {
                            onMainDispatcher {
                                enableLocalDns.isEnabled = stopped
                            }
                        }
                    }
                }
            }

            listener((activity as MainActivity).state)
            MainActivity.stateListener = listener
            routeMode.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    when (newValue) {
                        RouteMode.BYPASS_CHINA, RouteMode.BYPASS_LAN_CHINA -> {
                            enableLocalDns.isChecked = true
                            enableLocalDns.isEnabled = false
                            DataStore.enableLocalDNS = true
                        }
                        else -> {
                            enableLocalDns.isEnabled = true
                        }
                    }
                    true
                }
            serviceMode.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    isProxyApps.isEnabled = newValue == Key.MODE_VPN
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
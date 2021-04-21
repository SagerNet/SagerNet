package io.nekohasekai.sagernet.ui

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
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore

        runOnDefaultDispatcher {
            DataStore.initGlobal()
            onMainDispatcher {
                addPreferencesFromResource(R.xml.global_preferences)
            }
            val serviceMode = findPreference<Preference>(Key.SERVICE_MODE)!!
            val routeMode = findPreference<Preference>(Key.ROUTE_MODE)!!
            val allowAccess = findPreference<Preference>(Key.ALLOW_ACCESS)!!
            val ipv6Route = findPreference<Preference>(Key.IPV6_ROUTE)!!
            val preferIpv6 = findPreference<Preference>(Key.PREFER_IPV6)!!

            val remoteDns = findPreference<Preference>(Key.REMOTE_DNS)!!
            val enableLocalDns = findPreference<SwitchPreference>(Key.ENABLE_LOCAL_DNS)!!
            val portLocalDns = findPreference<EditTextPreference>(Key.LOCAL_DNS_PORT)!!
            val domesticDns = findPreference<EditTextPreference>(Key.DOMESTIC_DNS)!!
            portLocalDns.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
            val onRouteChange = Preference.OnPreferenceChangeListener { _, newValue ->
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

            val listener: (BaseService.State) -> Unit = {
                val stopped = it == BaseService.State.Stopped
                serviceMode.isEnabled = stopped
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


                //  portProxy.isEnabled = stopped
                /*   if (stopped) onServiceModeChange.onPreferenceChange(null, DataStore.serviceMode) else {
                       portTransproxy.isEnabled = false
                   }*/
            }
            listener((activity as MainActivity).state)
            MainActivity.stateListener = listener
            routeMode.onPreferenceChangeListener = onRouteChange
        }

    }

    override fun onDestroy() {
        MainActivity.stateListener = null
        super.onDestroy()
    }
}
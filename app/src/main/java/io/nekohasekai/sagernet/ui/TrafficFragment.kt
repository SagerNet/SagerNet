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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.PackageCache

class TrafficFragment : ToolbarFragment(R.layout.layout_traffic),
    Toolbar.OnMenuItemClickListener {

    lateinit var binding: LayoutTrafficBinding
    lateinit var adapter: TrafficAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setTitle(R.string.menu_traffic)
        toolbar.inflateMenu(R.menu.traffic_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutTrafficBinding.bind(view)
        adapter = TrafficAdapter()
        binding.trafficPager.adapter = adapter

        TabLayoutMediator(binding.trafficTab, binding.trafficPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.traffic_active)
                else -> getString(R.string.traffic_stats)
            }
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.attach()

        (requireActivity() as MainActivity).connection.trafficTimeout = 1500
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_traffic_statistics -> {
                (requireActivity() as MainActivity).connection.service?.resetTrafficStats()
                runOnDefaultDispatcher {
                    emitStats(emptyList())
                }
            }
        }
        return true
    }

    inner class TrafficAdapter() : FragmentStateAdapter(this) {

        override fun getItemCount(): Int {
            return 2
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ActiveFragment()
                else -> StatsFragment()
            }
        }

    }

    val listeners = mutableListOf<(List<AppStats>) -> Unit>()

    fun emitStats(statsList: List<AppStats>) {
        runOnDefaultDispatcher {
            for (listener in listeners) listener(statsList)
        }
    }

    override fun onStart() {
        super.onStart()

        (requireActivity() as MainActivity).connection.trafficTimeout = 1500
    }

    override fun onStop() {
        super.onStop()

        (requireActivity() as MainActivity).connection.trafficTimeout = 0
    }

    override fun onDestroy() {
        super.onDestroy()

        (requireActivity() as MainActivity).connection.trafficTimeout = 0
    }

    val createRule = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            (requireActivity() as MainActivity).ruleCreated()
        }
    }

    inner class ItemMenuListener(val stats: AppStats) : PopupMenu.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.copy_label -> {
                    val success = SagerNet.trySetPrimaryClip(PackageCache.loadLabel(stats.packageName))
                    snackbar(if (success) R.string.copy_success else R.string.copy_failed).show()
                }
                R.id.copy_package_name -> {
                    val success = SagerNet.trySetPrimaryClip(stats.packageName)
                    snackbar(if (success) R.string.copy_success else R.string.copy_failed).show()
                }

                R.id.open_app -> {
                    try {
                        val launch = app.packageManager.getLaunchIntentForPackage(stats.packageName)
                        if (launch == null) {
                            snackbar(R.string.app_no_launcher).show()
                        } else {
                            startActivity(launch)
                        }
                    } catch (e: Exception) {
                        snackbar(e.readableMessage).show()
                    }
                }
                R.id.open_settings -> {
                    try {
                        startActivity(Intent().apply {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = Uri.fromParts(
                                "package", stats.packageName, null
                            )
                        })
                    } catch (e: Exception) {
                        snackbar(e.readableMessage).show()
                    }
                }
                R.id.open_market -> {
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=${stats.packageName}")
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        requireContext().launchCustomTab("https://play.google.com/store/apps/details?id=${stats.packageName}")
                    }
                }
                R.id.create_rule -> {
                    createRule.launch(Intent(
                        requireContext(), RouteSettingsActivity::class.java
                    ).apply {
                        putExtra(RouteSettingsActivity.EXTRA_PACKAGE_NAME, stats.packageName)
                    })
                }
            }
            return true
        }

    }

}
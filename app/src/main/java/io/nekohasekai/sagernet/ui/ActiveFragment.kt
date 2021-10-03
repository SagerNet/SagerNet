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

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutTrafficItemBinding
import io.nekohasekai.sagernet.databinding.LayoutTrafficListBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.PackageCache

class ActiveFragment : Fragment(R.layout.layout_traffic_list) {

    lateinit var binding: LayoutTrafficListBinding
    lateinit var adapter: ActiveAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = LayoutTrafficListBinding.bind(view)
        adapter = ActiveAdapter()
        binding.trafficList.layoutManager = FixedLinearLayoutManager(binding.trafficList)
        binding.trafficList.adapter = adapter
        (parentFragment as TrafficFragment).listeners.add(::emitStats)
        emitStats(emptyList())
    }

    fun emitStats(statsList: List<AppStats>) {
        if (statsList.isEmpty()) {
            runOnMainDispatcher {
                binding.holder.isVisible = true
                binding.trafficList.isVisible = false

                if (!SagerNet.started || DataStore.serviceMode != Key.MODE_VPN) {
                    binding.holder.text = getString(R.string.traffic_holder)
                } else if ((activity as MainActivity).connection.service?.trafficStatsEnabled != true) {
                    binding.holder.text = getString(R.string.app_statistics_disabled)
                } else {
                    binding.holder.text = getString(R.string.no_statistics)
                }
            }
            binding.trafficList.post {
                adapter.data = emptyList()
                adapter.notifyDataSetChanged()
            }
        } else {
            runOnMainDispatcher {
                binding.holder.isVisible = false
                binding.trafficList.isVisible = true
            }

            val now = System.currentTimeMillis() / 1000
            val list = statsList.filter { it.deactivateAt == 0 || now - it.deactivateAt < 5 }
                .toSortedSet { a, b ->
                    val dataA = a.uplink + a.downlink
                    val dataB = b.uplink + b.downlink
                    if (dataA != dataB) {
                        dataB.compareTo(dataA)
                    } else {
                        val connA = a.tcpConnections + a.udpConnections
                        val connB = b.tcpConnections + b.udpConnections
                        if (connA != connB) {
                            connB.compareTo(connA)
                        } else {
                            b.packageName.compareTo(a.packageName)
                        }
                    }
                }
                .toList()
            binding.trafficList.post {
                adapter.data = list
                adapter.notifyDataSetChanged()
            }
        }
    }

    inner class ActiveAdapter : RecyclerView.Adapter<ActiveViewHolder>() {

        init {
            setHasStableIds(true)
        }

        lateinit var data: List<AppStats>

        override fun getItemId(position: Int): Long {
            return data[position].uid.toLong()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActiveViewHolder {
            return ActiveViewHolder(
                LayoutTrafficItemBinding.inflate(layoutInflater, parent, false)
            )
        }

        override fun onBindViewHolder(holder: ActiveViewHolder, position: Int) {
            holder.bind(data[position])
        }

        override fun getItemCount(): Int {
            if (!::data.isInitialized) return 0
            return data.size
        }
    }

    inner class ActiveViewHolder(val binding: LayoutTrafficItemBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {

        fun bind(stats: AppStats) {
            PackageCache.awaitLoadSync()

            val packageName = if (stats.uid > 1000) {
                PackageCache.uidMap[stats.uid]?.iterator()?.next() ?: "android"
            } else {
                "android"
            }

            binding.menu.setOnClickListener {
                val popup = PopupMenu(requireContext(), it)
                popup.menuInflater.inflate(R.menu.traffic_item_menu, popup.menu)
                popup.setOnMenuItemClickListener(
                    (requireParentFragment() as TrafficFragment).ItemMenuListener(
                        stats
                    )
                )
                popup.show()
            }

            binding.label.text = PackageCache.loadLabel(packageName)
            binding.desc.text = "$packageName (${stats.uid})"
            binding.tcpConnections.text = getString(R.string.tcp_connections, stats.tcpConnections)
            binding.udpConnections.text = getString(R.string.udp_connections, stats.udpConnections)
            binding.trafficUplink.text = getString(
                R.string.traffic_uplink,
                Formatter.formatFileSize(requireContext(), stats.uplinkTotal),
                Formatter.formatFileSize(requireContext(), stats.uplink)
            )
            binding.trafficDownlink.text = getString(
                R.string.traffic_downlink,
                Formatter.formatFileSize(requireContext(), stats.downlinkTotal),
                Formatter.formatFileSize(requireContext(), stats.downlink)
            )
            val info = PackageCache.installedApps[packageName]
            if (info != null) runOnDefaultDispatcher {
                try {
                    val icon = info.loadIcon(app.packageManager)
                    onMainDispatcher {
                        binding.icon.setImageDrawable(icon)
                    }
                } catch (ignored: Exception) {
                }
            }
        }

    }

}
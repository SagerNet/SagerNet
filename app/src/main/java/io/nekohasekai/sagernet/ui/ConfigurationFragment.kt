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

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.Executable
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.databinding.LayoutProfileBinding
import io.nekohasekai.sagernet.databinding.LayoutProfileListBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.fmt.v2ray.toV2rayN
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.profile.*
import io.nekohasekai.sagernet.utils.TestInstance
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.properties.Delegates

class ConfigurationFragment @JvmOverloads constructor(
    val select: Boolean = false,
    val selectedItem: ProxyEntity? = null,
) : ToolbarFragment(R.layout.layout_group_list), PopupMenu.OnMenuItemClickListener,
    Toolbar.OnMenuItemClickListener {

    lateinit var adapter: GroupPagerAdapter
    lateinit var tabLayout: TabLayout
    lateinit var groupPager: ViewPager2
    val selectedGroup get() = adapter.groupList[tabLayout.selectedTabPosition]
    val alwaysShowAddress by lazy { DataStore.alwaysShowAddress }
    val securityAdvisory by lazy { DataStore.securityAdvisory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!select) {
            toolbar.inflateMenu(R.menu.add_profile_menu)

            if (!isExpert) {
                toolbar.menu.findItem(R.id.action_connection_test).subMenu.removeItem(R.id.action_connection_url_reuse)
            }

            toolbar.setOnMenuItemClickListener(this)
        } else {
            toolbar.setTitle(R.string.select_profile)
            toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
            toolbar.setNavigationOnClickListener {
                requireActivity().finish()
            }
        }

        groupPager = view.findViewById(R.id.group_pager)
        tabLayout = view.findViewById(R.id.group_tab)
        adapter = GroupPagerAdapter()
        ProfileManager.addListener(adapter)

        groupPager.adapter = adapter
        groupPager.offscreenPageLimit = 2
        if (!select) {
            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    runOnDefaultDispatcher {
                        DataStore.selectedGroup = selectedGroup.id
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                }
            })
        }

        TabLayoutMediator(tabLayout, groupPager) { tab, position ->
            if (adapter.groupList.size > position) {
                tab.text = adapter.groupList[position].displayName()
            }
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.attach()

        toolbar.setOnClickListener {

            val fragment =
                (childFragmentManager.findFragmentByTag("f" + selectedGroup.id) as GroupFragment?)

            if (fragment != null) {
                val selectedProxy = selectedItem?.id ?: DataStore.selectedProxy
                val selectedProfileIndex =
                    fragment.adapter.configurationIdList.indexOf(selectedProxy)
                if (selectedProfileIndex != -1) {
                    val layoutManager = fragment.layoutManager
                    val first = layoutManager.findFirstVisibleItemPosition()
                    val last = layoutManager.findLastVisibleItemPosition()

                    if (selectedProfileIndex !in first..last) {
                        fragment.configurationListView.scrollTo(selectedProfileIndex, true)
                        return@setOnClickListener
                    }

                }

                fragment.configurationListView.scrollTo(0)
            }

        }
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) {
            ProfileManager.removeListener(adapter)
        }

        super.onDestroy()
    }

    val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) runOnDefaultDispatcher {
            try {
                val fileText =
                    requireContext().contentResolver.openInputStream(it)!!.bufferedReader()
                        .readText()
                val proxies = ProfileManager.parseSubscription(fileText)?.second
                if (proxies.isNullOrEmpty()) onMainDispatcher {
                    snackbar(getString(R.string.no_proxies_found_in_file)).show()
                } else import(proxies)
            } catch (e: Exception) {
                Logs.w(e)

                onMainDispatcher {
                    snackbar(e.readableMessage).show()
                }
            }
        }
    }

    suspend fun import(proxies: List<AbstractBean>) {
        val selectedGroup = selectedGroup
        var targetIndex by Delegates.notNull<Int>()
        val targetId = if (!selectedGroup.isSubscription) {
            selectedGroup.id
        } else {
            targetIndex = adapter.groupList.indexOfFirst { !it.isSubscription }
            adapter.groupList[targetIndex].id
        }

        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        onMainDispatcher {
            if (selectedGroup.id != targetId) {
                tabLayout.getTabAt(targetIndex)?.select()
            }

            snackbar(
                requireContext().resources.getQuantityString(
                    R.plurals.added, proxies.size, proxies.size
                )
            ).show()
        }

    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_qr_code -> {
                startActivity(Intent(context, ScannerActivity::class.java))
            }
            R.id.action_import_clipboard -> {
                val text = SagerNet.getClipboardText()
                if (text.isBlank()) {
                    snackbar(getString(R.string.clipboard_empty)).show()
                } else runOnDefaultDispatcher {
                    try {
                        val proxies = ProfileManager.parseSubscription(text)?.second
                        if (proxies.isNullOrEmpty()) onMainDispatcher {
                            snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                        } else import(proxies)
                    } catch (e: Exception) {
                        Logs.w(e)

                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }
            }
            R.id.action_import_file -> {
                importFile.launch("*/*")
            }
            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java))
            }
            R.id.action_new_http -> {
                startActivity(Intent(requireActivity(), HttpSettingsActivity::class.java))
            }
            R.id.action_new_ss -> {
                startActivity(Intent(requireActivity(), ShadowsocksSettingsActivity::class.java))
            }
            R.id.action_new_ssr -> {
                startActivity(Intent(requireActivity(), ShadowsocksRSettingsActivity::class.java))
            }
            R.id.action_new_vmess -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java))
            }
            R.id.action_new_vless -> {
                startActivity(Intent(requireActivity(), VLESSSettingsActivity::class.java))
            }
            R.id.action_new_trojan -> {
                startActivity(Intent(requireActivity(), TrojanSettingsActivity::class.java))
            }
            R.id.action_new_trojan_go -> {
                startActivity(Intent(requireActivity(), TrojanGoSettingsActivity::class.java))
            }
            R.id.action_new_naive -> {
                startActivity(Intent(requireActivity(), NaiveSettingsActivity::class.java))
            }
            R.id.action_new_ping_tunnel -> {
                startActivity(Intent(requireActivity(), PingTunnelSettingsActivity::class.java))
            }
            R.id.action_new_relay_baton -> {
                startActivity(Intent(requireActivity(), RelayBatonSettingsActivity::class.java))
            }
            R.id.action_new_brook -> {
                startActivity(Intent(requireActivity(), BrookSettingsActivity::class.java))
            }
            R.id.action_new_config -> {
                startActivity(Intent(requireActivity(), ConfigSettingsActivity::class.java))
            }
            R.id.action_new_chain -> {
                startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
            }
            R.id.action_new_balancer -> {
                startActivity(Intent(requireActivity(), BalancerSettingsActivity::class.java))
            }
            R.id.action_export_clipboard -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup)
                    val links = profiles.mapNotNull { it.toLink() }.joinToString("\n")
                    SagerNet.trySetPrimaryClip(links)
                    onMainDispatcher {
                        snackbar(getString(R.string.copy_toast_msg)).show()
                    }
                }
            }
            R.id.action_export_file -> {
                startFilesForResult(exportProfiles, "profiles.txt")
            }/* R.id.test -> {
                runOnDefaultDispatcher {
                    try {
                        val managedChannel = createChannel()
                        val observatoryService =
                            ObservatoryServiceGrpcKt.ObservatoryServiceCoroutineStub(managedChannel)
                        val status =
                            observatoryService.getOutboundStatus(GetOutboundStatusRequest.getDefaultInstance()).status
                        val message =
                            "${status.statusCount}\n" + status.statusList.joinToString("\n") { it.outboundTag + ": " + it.alive + ", " + it.delay + ", " + it.lastErrorReason }
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setMessage(message)
                                .setPositiveButton(android.R.string.ok, null).show()
                        }
                        managedChannel.shutdownNow()
                    } catch (e: StatusException) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setMessage(e.readableMessage)
                                .setPositiveButton(android.R.string.ok, null).show()
                        }
                    }
                }
            }*/
            R.id.action_clear_traffic_statistics -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup)
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.tx != 0L || profile.rx != 0L) {
                            profile.tx = 0
                            profile.rx = 0
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }
            R.id.action_connection_test_clear_results -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup)
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.status != 0) {
                            profile.status = 0
                            profile.ping = 0
                            profile.error = null
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }
            R.id.action_connection_icmp_ping -> {
                pingTest(true)
            }
            R.id.action_connection_tcp_ping -> {
                pingTest(false)
            }
            R.id.action_connection_url_test -> {
                urlTest(false)
            }
            R.id.action_connection_url_reuse -> {
                urlTest(true)
            }
            R.id.action_connection_reorder -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup)
                    val sorted = profiles.sortedBy { if (it.status == 1) it.ping else 114514 }
                    for (index in sorted.indices) {
                        sorted[index].userOrder = (index + 1).toLong()
                    }
                    SagerDatabase.proxyDao.updateProxy(sorted)
                    ProfileManager.postReload(DataStore.selectedGroup)

                }
            }
        }
        return true
    }

    inner class TestDialog {
        val binding = LayoutProgressBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(requireContext()).setView(binding.root)
            .setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener { _, _ ->
                cancel()
            }).setCancelable(false)
        lateinit var cancel: () -> Unit
        val results = ArrayList<ProxyEntity>()
        val adapter = TestAdapter()

        suspend fun insert(profile: ProxyEntity) {
            binding.listView.post {
                results.add(profile)
                adapter.notifyItemInserted(results.size - 1)
                binding.listView.scrollToPosition(results.size - 1)
            }
        }

        suspend fun update(profile: ProxyEntity) {
            binding.listView.post {
                val index = results.indexOf(profile)
                adapter.notifyItemChanged(index)
            }
        }

        init {
            binding.listView.layoutManager = FixedLinearLayoutManager(binding.listView)
            binding.listView.itemAnimator = DefaultItemAnimator()
            binding.listView.adapter = adapter
        }

        inner class TestAdapter : RecyclerView.Adapter<TestResultHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TestResultHolder(
                LayoutProfileBinding.inflate(layoutInflater, parent, false)
            )

            override fun onBindViewHolder(holder: TestResultHolder, position: Int) {
                holder.bind(results[position])
            }

            override fun getItemCount() = results.size
        }

        inner class TestResultHolder(val binding: LayoutProfileBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                binding.edit.isGone = true
                binding.share.isGone = true
            }

            fun bind(profile: ProxyEntity) {
                binding.profileName.text = profile.displayName()
                binding.profileType.text = profile.displayType()

                when (profile.status) {
                    -1 -> {
                        binding.profileStatus.text = profile.error
                        binding.profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                    }
                    0 -> {
                        binding.profileStatus.setText(R.string.connection_test_testing)
                        binding.profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                    }
                    1 -> {
                        binding.profileStatus.text = getString(R.string.available, profile.ping)
                        binding.profileStatus.setTextColor(requireContext().getColour(R.color.material_green_500))
                    }
                    2 -> {
                        binding.profileStatus.text = profile.error
                        binding.profileStatus.setTextColor(requireContext().getColour(R.color.material_red_500))
                    }
                    3 -> {
                        binding.profileStatus.setText(R.string.unavailable)
                        binding.profileStatus.setTextColor(requireContext().getColour(R.color.material_red_500))
                    }
                }

                if (profile.status == 3) {
                    binding.content.setOnClickListener {
                        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.error_title)
                            .setMessage(profile.error ?: "<?>")
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                } else {
                    binding.content.setOnClickListener {}
                }
            }
        }

    }

    fun stopService() {
        if (serviceStarted()) SagerNet.stopService()
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    fun pingTest(icmpPing: Boolean) {
        stopService()

        val test = TestDialog()
        val testJobs = mutableListOf<Job>()
        val dialog = test.builder.show()
        val mainJob = runOnDefaultDispatcher {
            val profiles =
                ConcurrentLinkedQueue(SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup))
            val testPool = newFixedThreadPoolContext(5, "Connection test pool")
            val icmpTestMethod by lazy {
                InetAddress::class.java.getDeclaredMethod("isReachableByICMP", Int::class.java)
            }
            repeat(5) {
                testJobs.add(launch(testPool) {
                    while (isActive) {
                        val profile = profiles.poll() ?: break

                        if (icmpPing) {
                            if (!profile.requireBean().canICMPing()) {
                                profile.status = -1
                                profile.error =
                                    app.getString(R.string.connection_test_icmp_ping_unavailable)
                                test.insert(profile)
                                continue
                            }
                        } else {
                            if (!profile.requireBean().canTCPing()) {
                                profile.status = -1
                                profile.error =
                                    app.getString(R.string.connection_test_tcp_ping_unavailable)
                                test.insert(profile)
                                continue
                            }
                        }

                        profile.status = 0
                        test.insert(profile)
                        var address = profile.requireBean().serverAddress
                        if (!address.isIpAddress()) {
                            try {
                                InetAddress.getAllByName(address).apply {
                                    if (isNotEmpty()) {
                                        address = this[0].hostAddress
                                    }
                                }
                            } catch (ignored: UnknownHostException) {
                            }
                        }
                        if (!isActive) break
                        if (!address.isIpAddress()) {
                            profile.status = 2
                            profile.error = app.getString(R.string.connection_test_domain_not_found)
                            test.update(profile)
                            continue
                        }
                        try {
                            if (icmpPing) {
                                val start = SystemClock.elapsedRealtime()
                                val result = icmpTestMethod.invoke(
                                    InetAddress.getByName(address), 5000
                                ) as Boolean
                                if (!isActive) break
                                if (result) {
                                    profile.status = 1
                                    profile.ping = (SystemClock.elapsedRealtime() - start).toInt()
                                } else {
                                    profile.status = 2
                                    profile.error = getString(R.string.connection_test_unreachable)
                                }
                                test.update(profile)
                            } else {
                                val socket = Socket()
                                socket.bind(InetSocketAddress(0))

                                protectFromVpn(socket.fileDescriptor)

                                val start = SystemClock.elapsedRealtime()
                                socket.connect(
                                    InetSocketAddress(
                                        address, profile.requireBean().serverPort
                                    ), 5000
                                )
                                if (!isActive) break
                                profile.status = 1
                                profile.ping = (SystemClock.elapsedRealtime() - start).toInt()
                                test.update(profile)
                                socket.close()
                            }
                        } catch (e: IOException) {
                            if (!isActive) break
                            val message = e.readableMessage

                            if (icmpPing) {
                                profile.status = 2
                                profile.error = getString(R.string.connection_test_unreachable)
                            } else {
                                profile.status = 2
                                when {
                                    !message.contains("failed:") -> profile.error =
                                        getString(R.string.connection_test_timeout)
                                    else -> when {
                                        message.contains("ECONNREFUSED") -> {
                                            profile.error =
                                                getString(R.string.connection_test_refused)
                                        }
                                        message.contains("ENETUNREACH") -> {
                                            profile.error =
                                                getString(R.string.connection_test_unreachable)
                                        }
                                        else -> {
                                            profile.status = 3
                                            profile.error = message
                                        }
                                    }
                                }
                            }
                            test.update(profile)
                        }
                    }
                })
            }

            testJobs.joinAll()
            testPool.close()

            ProfileManager.updateProfile(test.results.filter { it.status != 0 })

            onMainDispatcher {
                test.binding.progressCircular.isGone = true
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText(android.R.string.ok)
            }
        }
        test.cancel = {
            mainJob.cancel()
            testJobs.forEach { it.cancel() }
            runOnDefaultDispatcher {
                ProfileManager.updateProfile(test.results.filter { it.status != 0 })
            }
        }
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    fun urlTest(reuse: Boolean) {
        stopService()

        val test = TestDialog()
        val dialog = test.builder.show()
        val mainJob = runOnDefaultDispatcher {
            Executable.killAll()

            val profiles = LinkedList(SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup))
            val testPool = newFixedThreadPoolContext(5, "Connection test pool")
            val basePort = DataStore.socksPort
            var count = 0
            val testJobs = mutableListOf<Job>()

            while (isActive) {
                if (count < 5) {
                    count++;
                } else {
                    testJobs.joinAll()
                    testJobs.clear()
                    Executable.killAll()
                    count = 1
                }

                val port = basePort + 100 + count * 20

                val profile = profiles.poll() ?: break
                profile.status = 0
                test.insert(profile)

                testJobs.add(launch(testPool) {
                    try {
                        val result = TestInstance(
                            requireContext(), profile, port
                        ).doTest(if (reuse) 2 else 1)
                        profile.status = 1
                        profile.ping = result
                    } catch (e: PluginManager.PluginNotFoundException) {
                        profile.status = 2
                        profile.error = e.readableMessage
                    } catch (e: Exception) {
                        profile.status = 3
                        profile.error = e.readableMessage
                    }

                    test.update(profile)
                })
            }

            testPool.close()

            ProfileManager.updateProfile(test.results.filter { it.status != 0 })

            onMainDispatcher {
                test.binding.progressCircular.isGone = true
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText(android.R.string.ok)
            }
        }
        test.cancel = {
            mainJob.cancel()
            runOnDefaultDispatcher {
                ProfileManager.updateProfile(test.results.filter { it.status != 0 })
            }
        }
    }

    private val exportProfiles =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup)
                    val links = profiles.mapNotNull { it.toLink() }.joinToString("\n")
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(
                            data
                        )!!.bufferedWriter().use {
                            it.write(links)
                        }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

    inner class GroupPagerAdapter : FragmentStateAdapter(this), ProfileManager.GroupListener {

        var selectedGroupIndex = 0
        var groupList: ArrayList<ProxyGroup> = ArrayList()

        fun reload() {

            runOnDefaultDispatcher {
                groupList = ArrayList(SagerDatabase.groupDao.allGroups())
                if (groupList.isEmpty()) {
                    SagerDatabase.groupDao.createGroup(ProxyGroup(isDefault = true))
                    groupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }

                val selectedGroup = selectedItem?.groupId ?: DataStore.selectedGroup
                if (selectedGroup != 0L) {
                    val selectedIndex = groupList.indexOfFirst { it.id == selectedGroup }
                    selectedGroupIndex = selectedIndex

                    onMainDispatcher {
                        groupPager.setCurrentItem(selectedIndex, false)
                    }
                }

                onMainDispatcher {
                    notifyDataSetChanged()
                    val hideTab = groupList.size == 1 && groupList[0].isDefault
                    tabLayout.isGone = hideTab
                    toolbar.elevation = if (hideTab) 0F else dp2px(4).toFloat()
                }
            }
        }

        init {
            reload()
        }

        override fun getItemCount(): Int {
            return groupList.size
        }

        override fun createFragment(position: Int): Fragment {
            return GroupFragment().apply {
                proxyGroup = groupList[position]
                if (position == selectedGroupIndex) {
                    selected = true
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return groupList[position].id
        }

        override fun containsItem(itemId: Long): Boolean {
            return groupList.any { it.id == itemId }
        }

        override suspend fun onAdd(group: ProxyGroup) {
            tabLayout.post {
                groupList.add(group)

                if (groupList.all { it.isDefault }) tabLayout.post {
                    tabLayout.visibility = View.VISIBLE
                }

                notifyItemInserted(groupList.size - 1)
                tabLayout.getTabAt(groupList.size - 1)?.select()
            }
        }

        override suspend fun onRemoved(groupId: Long) {
            val index = groupList.indexOfFirst { it.id == groupId }
            if (index == -1) return

            tabLayout.post {
                groupList.removeAt(index)
                notifyItemRemoved(index)
            }
        }

        override suspend fun onUpdated(group: ProxyGroup) {
            val index = groupList.indexOfFirst { it.id == group.id }
            if (index == -1) return

            tabLayout.post {
                tabLayout.getTabAt(index)?.text = group.displayName()
            }
        }
    }

    class GroupFragment : Fragment() {

        lateinit var proxyGroup: ProxyGroup
        var selected = false
        var scrolled = false

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            return LayoutProfileListBinding.inflate(inflater).root
        }

        lateinit var undoManager: UndoSnackbarManager<ProxyEntity>
        lateinit var adapter: ConfigurationAdapter

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)

            if (::proxyGroup.isInitialized) {
                outState.putParcelable("proxyGroup", proxyGroup)
            }
        }

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)

            savedInstanceState?.getParcelable<ProxyGroup>("proxyGroup")?.also {
                proxyGroup = it
                onViewCreated(requireView(), null)
            }
        }

        private val isEnabled: Boolean
            get() {
                return ((activity as? MainActivity)
                    ?: return false).state.let { it.canStop || it == BaseService.State.Stopped }
            }

        private fun isProfileEditable(id: Long): Boolean {
            return ((activity as? MainActivity)
                ?: return false).state == BaseService.State.Stopped || id != DataStore.selectedProxy
        }

        lateinit var layoutManager: LinearLayoutManager
        lateinit var configurationListView: RecyclerView

        val select by lazy { (parentFragment as ConfigurationFragment).select }
        val selectedItem by lazy { (parentFragment as ConfigurationFragment).selectedItem }

        override fun onResume() {
            super.onResume()

            if (::configurationListView.isInitialized && configurationListView.size == 0) {
                configurationListView.adapter = adapter
                runOnDefaultDispatcher {
                    adapter.reloadProfiles(proxyGroup.id)
                }
            } else if (!::configurationListView.isInitialized) {
                onViewCreated(requireView(), null)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            if (!::proxyGroup.isInitialized) return

            configurationListView = view.findViewById(R.id.configuration_list)
            layoutManager = FixedLinearLayoutManager(configurationListView)
            configurationListView.layoutManager = layoutManager
            adapter = ConfigurationAdapter()
            ProfileManager.addListener(adapter)
            configurationListView.adapter = adapter
            configurationListView.setItemViewCacheSize(20)

            if (!select && !proxyGroup.isSubscription) {

                undoManager = UndoSnackbarManager(activity as MainActivity, adapter)

                ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
                ) {
                    override fun getSwipeDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ): Int {
                        return if (isProfileEditable((viewHolder as ConfigurationHolder).entity.id)) {
                            super.getSwipeDirs(recyclerView, viewHolder)
                        } else 0
                    }

                    override fun getDragDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ) = if (isEnabled) super.getDragDirs(recyclerView, viewHolder) else 0

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val index = viewHolder.bindingAdapterPosition
                        adapter.remove(index)
                        undoManager.remove(index to (viewHolder as ConfigurationHolder).entity)
                    }

                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
                    ): Boolean {
                        adapter.move(
                            viewHolder.bindingAdapterPosition, target.bindingAdapterPosition
                        )
                        return true
                    }

                    override fun clearView(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ) {
                        super.clearView(recyclerView, viewHolder)
                        adapter.commitMove()
                    }
                }).attachToRecyclerView(configurationListView)

            }

        }

        override fun onDestroy() {
            if (::adapter.isInitialized) {
                ProfileManager.removeListener(adapter)
            }

            super.onDestroy()

            if (!::undoManager.isInitialized) return
            undoManager.flush()
        }

        inner class ConfigurationAdapter : RecyclerView.Adapter<ConfigurationHolder>(),
            ProfileManager.Listener, UndoSnackbarManager.Interface<ProxyEntity> {

            var configurationIdList: MutableList<Long> = mutableListOf()
            val configurationList = HashMap<Long, ProxyEntity>()

            private fun getItem(profileId: Long): ProxyEntity {
                var profile = configurationList[profileId]
                if (profile == null) {
                    profile = ProfileManager.getProfile(profileId)
                    if (profile != null) {
                        configurationList[profileId] = profile
                    }
                }
                return profile!!
            }

            private fun getItemAt(index: Int) = getItem(configurationIdList[index])

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): ConfigurationHolder {
                return ConfigurationHolder(
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.layout_profile, parent, false
                    )
                )
            }

            override fun getItemId(position: Int): Long {
                return configurationIdList[position]
            }

            override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) {
                try {
                    holder.bind(getItemAt(position))
                } catch (ignored: NullPointerException) { // when group deleted
                }
            }

            override fun getItemCount(): Int {
                return configurationIdList.size
            }

            private val updated = HashSet<ProxyEntity>()

            fun move(from: Int, to: Int) {
                val first = getItemAt(from)
                var previousOrder = first.userOrder
                val (step, range) = if (from < to) Pair(1, from until to) else Pair(
                    -1, to + 1 downTo from
                )
                for (i in range) {
                    val next = getItemAt(i + step)
                    val order = next.userOrder
                    next.userOrder = previousOrder
                    previousOrder = order
                    configurationIdList[i] = next.id
                    updated.add(next)
                }
                first.userOrder = previousOrder
                configurationIdList[to] = first.id
                updated.add(first)
                notifyItemMoved(from, to)
            }

            fun commitMove() = runOnDefaultDispatcher {
                updated.forEach { SagerDatabase.proxyDao.updateProxy(it) }
                updated.clear()
            }

            fun remove(pos: Int) {
                configurationIdList.removeAt(pos)
                notifyItemRemoved(pos)
            }

            override fun undo(actions: List<Pair<Int, ProxyEntity>>) {
                for ((index, item) in actions) {
                    configurationListView.post {
                        configurationList[item.id] = item
                        configurationIdList.add(index, item.id)
                        notifyItemInserted(index)
                    }
                }
            }

            override fun commit(actions: List<Pair<Int, ProxyEntity>>) {
                val profiles = actions.map { it.second }
                runOnDefaultDispatcher {
                    for (entity in profiles) {
                        ProfileManager.deleteProfile(entity.groupId, entity.id)
                    }
                }
            }

            override suspend fun onAdd(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return

                configurationListView.post {
                    if (::undoManager.isInitialized) {
                        undoManager.flush()
                    }
                    val pos = itemCount
                    configurationList[profile.id] = profile
                    configurationIdList.add(profile.id)
                    notifyItemInserted(pos)
                }
            }

            override suspend fun onUpdated(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return
                val index = configurationIdList.indexOf(profile.id)
                if (index < 0) return
                configurationListView.post {
                    if (::undoManager.isInitialized) {
                        undoManager.flush()
                    }
                    configurationList[profile.id] = profile
                    notifyItemChanged(index)
                }
            }

            override suspend fun onUpdated(profileId: Long, trafficStats: TrafficStats) {
                val index = configurationIdList.indexOf(profileId)
                if (index != -1) {
                    val holder = layoutManager.findViewByPosition(index)
                        ?.let { configurationListView.getChildViewHolder(it) } as ConfigurationHolder?
                    if (holder != null) {
                        holder.entity.stats = trafficStats
                        onMainDispatcher {
                            holder.bind(holder.entity)
                        }
                    }
                }
            }

            override suspend fun onRemoved(groupId: Long, profileId: Long) {
                if (groupId != proxyGroup.id) return
                val index = configurationIdList.indexOf(profileId)
                if (index < 0) return
                configurationListView.post {
                    configurationIdList.removeAt(index)
                    configurationList.remove(profileId)
                    notifyItemRemoved(index)
                }
            }

            override suspend fun onCleared(groupId: Long) {
                if (groupId != proxyGroup.id) return
                reloadProfiles(groupId)
            }

            override suspend fun reloadProfiles(groupId: Long) {
                if (groupId != proxyGroup.id) return

                val newProfiles = SagerDatabase.proxyDao.getIdsByGroup(proxyGroup.id)

                var selectedProfileIndex = -1

                if (selected) {
                    val selectedProxy = selectedItem?.id ?: DataStore.selectedProxy
                    selectedProfileIndex = newProfiles.indexOf(selectedProxy)
                }

                configurationListView.post {
                    configurationIdList.clear()
                    configurationIdList.addAll(newProfiles)
                    notifyDataSetChanged()

                    if (selectedProfileIndex != -1) {
                        configurationListView.scrollTo(selectedProfileIndex, true)
                    } else if (newProfiles.isNotEmpty()) {
                        configurationListView.scrollTo(0, true)
                    }

                }

                if (newProfiles.isEmpty() && proxyGroup.isDefault) {
                    val created = ProfileManager.createProfile(groupId, SOCKSBean().apply {
                        name = "Local tunnel"
                        initDefaultValues()
                    })
                    if (DataStore.selectedProxy == 0L) {
                        DataStore.selectedProxy = created.id
                    }
                }
            }

        }

        interface ConfigurationHolderImpl {
            fun bind(proxyEntity: ProxyEntity)
        }

        inner class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view),
            PopupMenu.OnMenuItemClickListener {

            lateinit var entity: ProxyEntity

            val profileName: TextView = view.findViewById(R.id.profile_name)
            val profileType: TextView = view.findViewById(R.id.profile_type)
            val profileAddress: TextView = view.findViewById(R.id.profile_address)
            val profileStatus: TextView = view.findViewById(R.id.profile_status)

            val trafficText: TextView = view.findViewById(R.id.traffic_text)
            val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
            val editButton: ImageView = view.findViewById(R.id.edit)
            val shareLayout: LinearLayout = view.findViewById(R.id.share)
            val shareLayer: LinearLayout = view.findViewById(R.id.share_layer)
            val shareButton: ImageView = view.findViewById(R.id.shareIcon)

            fun bind(proxyEntity: ProxyEntity) {
                entity = proxyEntity

                if (select) {
                    view.setOnClickListener {
                        (requireActivity() as ProfileSelectActivity).returnProfile(proxyEntity.id)
                    }
                } else {
                    view.setOnClickListener {
                        runOnDefaultDispatcher {
                            if (DataStore.selectedProxy != proxyEntity.id) {
                                val lastSelected = DataStore.selectedProxy
                                DataStore.selectedProxy = proxyEntity.id
                                ProfileManager.postUpdate(lastSelected)
                                onMainDispatcher {
                                    selectedView.visibility = View.VISIBLE
                                    if ((activity as MainActivity).state.canStop) SagerNet.reloadService()
                                }
                            }
                        }

                    }
                }

                profileName.text = proxyEntity.displayName()
                profileType.text = proxyEntity.displayType()

                var rx = proxyEntity.rx
                var tx = proxyEntity.tx

                val stats = proxyEntity.stats
                if (stats != null) {
                    rx += stats.rxTotal
                    tx += stats.txTotal
                }

                val showTraffic = rx + tx != 0L
                trafficText.isVisible = showTraffic
                if (showTraffic) {
                    trafficText.text = view.context.getString(
                        R.string.traffic,
                        Formatter.formatFileSize(view.context, tx),
                        Formatter.formatFileSize(view.context, rx)
                    )
                }

                var address = proxyEntity.displayAddress()
                if (showTraffic && address.length >= 30) {
                    address = address.substring(0, 27) + "..."
                }

                val pf = requireParentFragment() as ConfigurationFragment

                if (proxyEntity.requireBean().name.isNotBlank()) {
                    if (!pf.alwaysShowAddress) {
                        address = ""
                    }
                }

                profileAddress.text = address
                (trafficText.parent as View).isGone =
                    (!showTraffic || proxyEntity.status <= 0) && address.isBlank()

                if (proxyEntity.status <= 0) {
                    if (showTraffic) {
                        profileStatus.text = trafficText.text
                        profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                        trafficText.text = ""
                    } else {
                        profileStatus.text = ""
                    }
                } else if (proxyEntity.status == 1) {
                    profileStatus.text = getString(R.string.available, proxyEntity.ping)
                    profileStatus.setTextColor(requireContext().getColour(R.color.material_green_500))
                } else {
                    profileStatus.setTextColor(requireContext().getColour(R.color.material_red_500))
                    if (proxyEntity.status == 2) {
                        profileStatus.text = proxyEntity.error
                    }
                }

                if (proxyEntity.status == 3) {
                    profileStatus.setText(R.string.unavailable)
                    profileStatus.setOnClickListener {
                        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.error_title)
                            .setMessage(proxyEntity.error ?: "<?>")
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                } else {
                    profileStatus.setOnClickListener(null)
                }

                editButton.setOnClickListener {
                    it.context.startActivity(
                        proxyEntity.settingIntent(
                            it.context, proxyGroup.isSubscription
                        )
                    )
                }

                shareLayout.isGone = select
                editButton.isGone = select

                runOnDefaultDispatcher {
                    val selected = (selectedItem?.id ?: DataStore.selectedProxy) == proxyEntity.id
                    val started = serviceStarted() && DataStore.startedProxy == proxyEntity.id
                    onMainDispatcher {
                        editButton.isEnabled = !started
                        selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    }

                    fun showShare(anchor: View) {
                        val popup = PopupMenu(requireContext(), anchor)
                        popup.menuInflater.inflate(R.menu.profile_share_menu, popup.menu)

                        if (proxyEntity.vmessBean == null) {
                            popup.menu.findItem(R.id.action_group_qr).subMenu.removeItem(R.id.action_v2rayn_qr)
                            popup.menu.findItem(R.id.action_group_clipboard).subMenu.removeItem(
                                R.id.action_v2rayn_clipboard
                            )
                        }

                        if (proxyEntity.configBean != null) {

                            popup.menu.findItem(R.id.action_group_qr).subMenu.removeItem(R.id.action_standard_qr)
                            popup.menu.findItem(R.id.action_group_clipboard).subMenu.removeItem(
                                R.id.action_standard_clipboard
                            )
                        } else if (!proxyEntity.haveLink()) {
                            popup.menu.removeItem(R.id.action_group_qr)
                            popup.menu.removeItem(R.id.action_group_clipboard)
                        }

                        if (proxyEntity.ptBean != null || proxyEntity.brookBean != null) {
                            popup.menu.removeItem(R.id.action_group_configuration)
                        }

                        popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                        popup.show()
                    }

                    if (!(select || proxyEntity.type == 8)) {

                        val validateResult = if (pf.securityAdvisory) {
                            proxyEntity.requireBean().isInsecure()
                        } else ResultLocal

                        when (validateResult) {
                            is ResultInsecure -> onMainDispatcher {
                                shareLayout.isVisible = true

                                shareLayer.setBackgroundColor(Color.RED)
                                shareButton.setImageResource(R.drawable.ic_baseline_warning_24)
                                shareButton.setColorFilter(Color.WHITE)

                                shareLayout.setOnClickListener {
                                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.insecure)
                                        .setMessage(resources.openRawResource(validateResult.textRes)
                                            .bufferedReader().use { it.readText() })
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            showShare(it)
                                        }.show().apply {
                                            findViewById<TextView>(android.R.id.message)?.apply {
                                                Linkify.addLinks(this, Linkify.WEB_URLS)
                                                movementMethod = LinkMovementMethod.getInstance()
                                            }
                                        }
                                }
                            }
                            is ResultDeprecated -> onMainDispatcher {
                                shareLayout.isVisible = true

                                shareLayer.setBackgroundColor(Color.YELLOW)
                                shareButton.setImageResource(R.drawable.ic_baseline_warning_24)
                                shareButton.setColorFilter(Color.GRAY)

                                shareLayout.setOnClickListener {
                                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.deprecated)
                                        .setMessage(resources.openRawResource(validateResult.textRes)
                                            .bufferedReader().use { it.readText() })
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            showShare(it)
                                        }.show().apply {
                                            findViewById<TextView>(android.R.id.message)?.apply {
                                                Linkify.addLinks(this, Linkify.WEB_URLS)
                                                movementMethod = LinkMovementMethod.getInstance()
                                            }
                                        }
                                }
                            }
                            else -> onMainDispatcher {
                                shareLayer.setBackgroundColor(Color.TRANSPARENT)
                                shareButton.setImageResource(R.drawable.ic_social_share)
                                shareButton.setColorFilter(Color.GRAY)

                                shareLayout.setOnClickListener {
                                    showShare(it)
                                }
                            }
                        }
                    }
                }

            }

            fun showCode(link: String) {
                QRCodeDialog(link).showAllowingStateLoss(parentFragmentManager)
            }

            fun export(link: String) {
                val success = SagerNet.trySetPrimaryClip(link)
                (activity as MainActivity).snackbar(if (success) R.string.action_export_msg else R.string.action_export_err)
                    .show()
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                try {
                    when (item.itemId) {
                        R.id.action_standard_qr -> showCode(entity.toLink()!!)
                        R.id.action_standard_clipboard -> export(entity.toLink()!!)
                        R.id.action_universal_qr -> showCode(
                            entity.requireBean().toUniversalLink()
                        )
                        R.id.action_universal_clipboard -> export(
                            entity.requireBean().toUniversalLink()
                        )
                        R.id.action_v2rayn_qr -> showCode(entity.vmessBean!!.toV2rayN())
                        R.id.action_v2rayn_clipboard -> export(entity.vmessBean!!.toV2rayN())
                        R.id.action_config_export_clipboard -> export(entity.exportConfig().first)
                        R.id.action_config_export_file -> {
                            val cfg = entity.exportConfig()
                            DataStore.serverConfig = cfg.first
                            startFilesForResult(
                                (parentFragment as ConfigurationFragment).exportConfig, cfg.second
                            )
                        }
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    (activity as MainActivity).snackbar(e.readableMessage).show()
                    return true
                }
                return true
            }
        }

    }

    private val exportConfig =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(
                            data
                        )!!.bufferedWriter().use {
                            it.write(DataStore.serverConfig)
                        }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

}
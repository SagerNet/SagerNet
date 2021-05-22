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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.profile.*
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import okhttp3.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.properties.Delegates


class ConfigurationFragment @JvmOverloads constructor(
    val select: Boolean = false,
) : ToolbarFragment(R.layout.layout_group_list),
    PopupMenu.OnMenuItemClickListener, Toolbar.OnMenuItemClickListener {

    lateinit var adapter: GroupPagerAdapter
    lateinit var tabLayout: TabLayout
    lateinit var groupPager: ViewPager2
    val selectedGroup get() = adapter.groupList[tabLayout.selectedTabPosition]

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!select) {
            toolbar.inflateMenu(R.menu.add_profile_menu)
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

        TabLayoutMediator(tabLayout, groupPager) { tab, position ->
            if (adapter.groupList.size > position) {
                tab.text = adapter.groupList[position].displayName()
            }
            /* tab.view.setOnLongClickListener { tabView ->
                 val popup = PopupMenu(requireContext(), tabView)
                 popup.menuInflater.inflate(R.menu.tab_edit_menu, popup.menu)
                 popup.setOnMenuItemClickListener(this)
                 popup.show()
                 true
             }*/
        }.attach()

        toolbar.setOnClickListener {

            val fragment =
                (childFragmentManager.findFragmentByTag("f" + selectedGroup.id) as GroupFragment?)

            if (fragment != null) {
                val selectedProxy = DataStore.selectedProxy
                val selectedProfileIndex =
                    fragment.adapter.configurationIdList.indexOf(selectedProxy)
                if (selectedProfileIndex != -1) {
                    val layoutManager = fragment.layoutManager as LinearLayoutManager
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
        runOnDefaultDispatcher {
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
                    Toast.makeText(app, e.readableMessage, Toast.LENGTH_LONG).show()
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
                    R.plurals.added,
                    proxies.size,
                    proxies.size
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
                    val proxies = ProfileManager.parseSubscription(text)?.second
                    if (proxies.isNullOrEmpty()) onMainDispatcher {
                        snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                    } else import(proxies)
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
            R.id.action_new_chain -> {
                startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
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
                startFilesForResult(exportProfiles)
            }
            R.id.action_clear -> {
                runOnDefaultDispatcher {
                    ProfileManager.clearGroup(DataStore.selectedGroup)
                }
            }
        }
        return true
    }

    private fun startFilesForResult(launcher: ActivityResultLauncher<String>) {
        try {
            return launcher.launch("")
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
        (activity as MainActivity).snackbar(getString(R.string.file_manager_missing)).show()
    }

    class SaveProfiles : ActivityResultContracts.CreateDocument() {
        override fun createIntent(context: Context, input: String) =
            super.createIntent(context, "profiles.txt").apply { type = "text/plain" }
    }


    private val exportProfiles = registerForActivityResult(SaveProfiles()) { data ->
        if (data != null) {
            runOnDefaultDispatcher {
                val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup)
                val links = profiles.mapNotNull { it.toLink() }.joinToString("\n")
                try {
                    (requireActivity() as MainActivity).contentResolver.openOutputStream(data)!!
                        .bufferedWriter().use {
                            it.write(links)
                        }
                    onMainDispatcher {
                        snackbar(getString(R.string.copy_toast_msg)).show()
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

                val selectedGroup = DataStore.selectedGroup
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
            return inflater.inflate(R.layout.layout_profile_list, container, false)
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
                return ((activity as? MainActivity) ?: return false)
                    .state.let { it.canStop || it == BaseService.State.Stopped }
            }

        private fun isProfileEditable(id: Long): Boolean {
            return ((activity as? MainActivity) ?: return false)
                .state == BaseService.State.Stopped || id != DataStore.selectedProxy
        }

        lateinit var layoutManager: LinearLayoutManager
        lateinit var configurationListView: RecyclerView

        val select by lazy { (parentFragment as ConfigurationFragment).select }

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
            layoutManager = if (proxyGroup.type != 1) {
                FixedLinearLayoutManager(configurationListView)
            } else {
                FixedGridLayoutManager(configurationListView, 2)
            }
            configurationListView.layoutManager = layoutManager
            adapter = ConfigurationAdapter()
            ProfileManager.addListener(adapter)
            configurationListView.adapter = adapter
            configurationListView.setItemViewCacheSize(20)

            if (!select && !proxyGroup.isSubscription) {

                undoManager =
                    UndoSnackbarManager(activity as MainActivity, adapter)

                ItemTouchHelper(object :
                    ItemTouchHelper.SimpleCallback(
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                        ItemTouchHelper.START
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
                        adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
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
                    LayoutInflater.from(parent.context)
                        .inflate(
                            if (proxyGroup.type != 1) R.layout.layout_profile else R.layout.layout_profile_clash,
                            parent,
                            false
                        )
                )
            }

            override fun getItemId(position: Int): Long {
                return configurationIdList[position]
            }

            override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) {
                try {
                    holder.bind(getItemAt(position))
                } catch (ignored: NullPointerException) {
                    // when group deleted
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
                    -1,
                    to + 1 downTo from
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

                if (selected && !scrolled) {
                    scrolled = true
                    val selectedProxy = DataStore.selectedProxy
                    selectedProfileIndex = newProfiles.indexOf(selectedProxy)
                }

                configurationListView.post {
                    configurationIdList.clear()
                    configurationIdList.addAll(newProfiles)
                    notifyDataSetChanged()

                    if (selectedProfileIndex != -1) {
                        configurationListView.scrollTo(selectedProfileIndex, true)
                    }

                }

                if (newProfiles.isEmpty() && proxyGroup.isDefault) {
                    val created = ProfileManager.createProfile(groupId,
                        SOCKSBean().apply {
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
            val impl =
                if (proxyGroup.type != 1) DefaultConfigurationHolderImpl() else ClashConfigurationHolderImpl()

            fun bind(proxyEntity: ProxyEntity) {
                entity = proxyEntity
                impl.bind(proxyEntity)
            }

            inner class DefaultConfigurationHolderImpl : ConfigurationHolderImpl {

                val profileName: TextView = view.findViewById(R.id.profile_name)
                val profileType: TextView = view.findViewById(R.id.profile_type)
                val profileAddress: TextView = view.findViewById(R.id.profile_address)
                val trafficText: TextView = view.findViewById(R.id.traffic_text)
                val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
                val editButton: ImageView = view.findViewById(R.id.edit)
                val shareLayout: LinearLayout = view.findViewById(R.id.share)
                val shareLayer: LinearLayout = view.findViewById(R.id.share_layer)
                val shareButton: ImageView = view.findViewById(R.id.shareIcon)

                override fun bind(proxyEntity: ProxyEntity) {

                    if (select) {
                        view.setOnClickListener {
                            (requireActivity() as ProfileSelectActivity).returnProfile(
                                proxyEntity.id
                            )
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
                    //  (trafficText.parent as View).isGone = !showTraffic && proxyGroup.isSubscription

                    editButton.setOnClickListener {
                        it.context.startActivity(
                            proxyEntity.settingIntent(
                                it.context,
                                proxyGroup.isSubscription
                            )
                        )
                    }

                    shareLayout.isGone = select || proxyEntity.type == 8
                    editButton.isGone = select

                    runOnDefaultDispatcher {
                        if (!select) {
                            val selected = DataStore.selectedProxy == proxyEntity.id
                            val started =
                                serviceStarted() && DataStore.startedProxy == proxyEntity.id
                            onMainDispatcher {
                                editButton.isEnabled = !started
                                selectedView.visibility =
                                    if (selected) View.VISIBLE else View.INVISIBLE
                            }
                        }

                        if (!(select || proxyEntity.type == 8)) {

                            val validateResult =
                                if (DataStore.securityAdvisory) {
                                    proxyEntity.requireBean().isInsecure()
                                } else ResultLocal

                            when (validateResult) {
                                is ResultInsecure -> onMainDispatcher {
                                    shareLayout.isVisible = true

                                    shareLayer.setBackgroundColor(Color.RED)
                                    shareButton.setImageResource(R.drawable.ic_baseline_warning_24)
                                    shareButton.setColorFilter(Color.WHITE)

                                    shareLayout.setOnClickListener {
                                        AlertDialog.Builder(requireContext())
                                            .setTitle(R.string.insecure)
                                            .setMessage(resources.openRawResource(validateResult.textRes)
                                                .bufferedReader().use { it.readText() })
                                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                                val popup = PopupMenu(requireContext(), it)
                                                popup.menuInflater.inflate(
                                                    R.menu.socks_share_menu,
                                                    popup.menu
                                                )
                                                popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                                                popup.show()
                                            }
                                            .show().apply {
                                                findViewById<TextView>(android.R.id.message)?.apply {
                                                    Linkify.addLinks(this, Linkify.WEB_URLS)
                                                    movementMethod =
                                                        LinkMovementMethod.getInstance()
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
                                        AlertDialog.Builder(requireContext())
                                            .setTitle(R.string.deprecated)
                                            .setMessage(resources.openRawResource(validateResult.textRes)
                                                .bufferedReader().use { it.readText() })
                                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                                val popup = PopupMenu(requireContext(), it)
                                                popup.menuInflater.inflate(
                                                    R.menu.socks_share_menu,
                                                    popup.menu
                                                )
                                                popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                                                popup.show()
                                            }
                                            .show().apply {
                                                findViewById<TextView>(android.R.id.message)?.apply {
                                                    Linkify.addLinks(this, Linkify.WEB_URLS)
                                                    movementMethod =
                                                        LinkMovementMethod.getInstance()
                                                }
                                            }
                                    }
                                }
                                else -> onMainDispatcher {
                                    shareLayer.setBackgroundColor(Color.TRANSPARENT)
                                    shareButton.setImageResource(R.drawable.ic_social_share)
                                    shareButton.setColorFilter(Color.GRAY)

                                    shareLayout.setOnClickListener {
                                        val popup = PopupMenu(requireContext(), it)
                                        popup.menuInflater.inflate(
                                            R.menu.socks_share_menu,
                                            popup.menu
                                        )
                                        popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                                        popup.show()
                                    }
                                }
                            }
                        }
                    }

                }

            }

            inner class ClashConfigurationHolderImpl : ConfigurationHolderImpl {

                val profileName: TextView = view.findViewById(R.id.profile_name)
                val profileType: TextView = view.findViewById(R.id.profile_type)
                val selectedView: LinearLayout = view.findViewById(R.id.selected_view)

                override fun bind(proxyEntity: ProxyEntity) {

                    view.setOnClickListener {
                        if (select) {
                            (requireActivity() as ProfileSelectActivity).returnProfile(proxyEntity.id)
                        } else {
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

                    if (!select) {

                        runOnDefaultDispatcher {
                            val selected = DataStore.selectedProxy == proxyEntity.id
                            onMainDispatcher {
                                selectedView.visibility =
                                    if (selected) View.VISIBLE else View.INVISIBLE
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
                (activity as MainActivity)
                    .snackbar()
                    .setText(if (success) R.string.action_export_msg else R.string.action_export_err)
                    .show()
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                try {
                    when (item.itemId) {
                        // socks
                        R.id.action_qr_code -> {
                            showCode(entity.toLink()!!)
                        }
                        R.id.action_export_clipboard -> {
                            export(entity.toLink()!!)
                        }
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    (activity as MainActivity).snackbar().setText(e.readableMessage).show()
                    return true
                }
                return true
            }
        }

    }

}
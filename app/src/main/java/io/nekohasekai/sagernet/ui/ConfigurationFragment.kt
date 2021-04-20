package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.settings.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.settings.SocksSettingsActivity
import io.nekohasekai.sagernet.widget.UndoSnackbarManager

class ConfigurationFragment : ToolbarFragment(R.layout.group_list_main),
    Toolbar.OnMenuItemClickListener,
    PopupMenu.OnMenuItemClickListener {

    lateinit var adapter: GroupPagerAdapter
    lateinit var tabLayout: TabLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.inflateMenu(R.menu.profile_manager_menu)
        toolbar.setOnMenuItemClickListener(this)

        val groupPager = view.findViewById<ViewPager2>(R.id.group_pager)
        tabLayout = view.findViewById(R.id.group_tab)
        adapter = GroupPagerAdapter()
        groupPager.adapter = adapter

        TabLayoutMediator(tabLayout, groupPager) { tab, position ->
            tab.text = adapter.groupList[position].name
                .takeIf { !it.isNullOrBlank() } ?: getString(R.string.group_default)
            tab.view.setOnLongClickListener { tabView ->
                val popup = PopupMenu(requireContext(), tabView)
                popup.menuInflater.inflate(R.menu.tab_edit_menu, popup.menu)
                popup.setOnMenuItemClickListener(this)
                popup.show()
                true
            }
        }.attach()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val selectGroup = adapter.groupList[tabLayout.selectedTabPosition]
        when (item.itemId) {
            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java).apply {
                    putExtra(ProfileSettingsActivity.EXTRA_GROUP_ID, selectGroup.id)
                })
            }
        }
        return true
    }

    inner class GroupPagerAdapter : FragmentStateAdapter(this) {

        var groupList: ArrayList<ProxyGroup> = ArrayList()

        init {
            runOnDefaultDispatcher {
                groupList = ArrayList(SagerDatabase.groupDao.allGroups())
                if (groupList.isEmpty()) {
                    SagerDatabase.groupDao.createGroup(ProxyGroup(isDefault = true))
                    groupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }
                onMainDispatcher {
                    notifyDataSetChanged()
                    val hideTab = groupList.size == 1 && groupList[0].isDefault
                    tabLayout.isGone = hideTab
                    toolbar.elevation = if (hideTab) 0F else dp2px(4).toFloat()
                }
            }
        }

        override fun getItemCount(): Int {
            return groupList.size
        }

        override fun createFragment(position: Int): Fragment {
            return GroupFragment().apply {
                proxyGroup = groupList[position]
            }
        }

        override fun getItemId(position: Int): Long {
            return groupList[position].id
        }

        override fun containsItem(itemId: Long): Boolean {
            return groupList.any { it.id == itemId }
        }

    }

    class GroupFragment : Fragment() {

        lateinit var proxyGroup: ProxyGroup

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            return inflater.inflate(R.layout.configurtion_list_main, container, false)
        }

        lateinit var undoManager: UndoSnackbarManager<ProxyEntity>
        lateinit var adapter: ConfigurationAdapter

        private val isEnabled get() = (activity as MainActivity).state.let { it.canStop || it == BaseService.State.Stopped }
        private fun isProfileEditable(id: Long) =
            (activity as MainActivity).state == BaseService.State.Stopped || id != DataStore.selectedProxy

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            if (!::proxyGroup.isInitialized) return

            val configurationList = view.findViewById<RecyclerView>(R.id.configuration_list).apply {
                layoutManager = when (proxyGroup.layout) {
                    else -> LinearLayoutManager(view.context)
                }
            }
            adapter = ConfigurationAdapter()
            configurationList.adapter = adapter
            undoManager =
                UndoSnackbarManager(activity as MainActivity, adapter::undo, adapter::commit)
            ItemTouchHelper(object :
                ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    ItemTouchHelper.START) {
                override fun getSwipeDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) = if (isProfileEditable((viewHolder).itemId)) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else 0

                override fun getDragDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) = if (isEnabled) super.getDragDirs(recyclerView, viewHolder) else 0

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val index = viewHolder.adapterPosition
                    adapter.remove(index)
                    undoManager.remove(index to (viewHolder as ConfigurationHolder).item)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
                ): Boolean {
                    adapter.move(viewHolder.adapterPosition, target.adapterPosition)
                    return true
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)
                    adapter.commitMove()
                }
            }).attachToRecyclerView(configurationList)
        }


        inner class ConfigurationAdapter : RecyclerView.Adapter<ConfigurationHolder>(),
            ProfileManager.Listener {

            var configurationList: MutableList<ProxyEntity> = mutableListOf()

            init {
                reloadProfiles(proxyGroup.id)
                ProfileManager.addListener(this)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigurationHolder {
                return ConfigurationHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.layout_profile, parent, false)
                )
            }

            override fun getItemId(position: Int): Long {
                return configurationList[position].id
            }

            override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) {
                holder.bind(configurationList[position])
            }

            override fun getItemCount(): Int {
                return configurationList.size
            }

            private val updated = HashSet<ProxyEntity>()

            fun move(from: Int, to: Int) {
                val first = configurationList[from]
                var previousOrder = first.userOrder
                val (step, range) = if (from < to) Pair(1, from until to) else Pair(-1,
                    to + 1 downTo from)
                for (i in range) {
                    val next = configurationList[i + step]
                    val order = next.userOrder
                    next.userOrder = previousOrder
                    previousOrder = order
                    configurationList[i] = next
                    updated.add(next)
                }
                first.userOrder = previousOrder
                configurationList[to] = first
                updated.add(first)
                notifyItemMoved(from, to)
            }

            fun commitMove() {
                updated.forEach { SagerDatabase.proxyDao.updateProxy(it) }
                updated.clear()
            }

            fun remove(pos: Int) {
                configurationList.removeAt(pos)
                notifyItemRemoved(pos)
            }

            fun undo(actions: List<Pair<Int, ProxyEntity>>) {
                for ((index, item) in actions) {
                    configurationList.add(index, item)
                    notifyItemInserted(index)
                }
            }

            fun commit(actions: List<Pair<Int, ProxyEntity>>) {
                for ((_, item) in actions) {
                    ProfileManager.deleteProfile(item.groupId, item.id)
                }
            }

            override fun onAdd(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return
                undoManager.flush()
                val pos = itemCount
                configurationList.add(profile)
                notifyItemInserted(pos)
            }

            override fun onUpdated(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return
                undoManager.flush()
                runOnDefaultDispatcher {
                    val index = configurationList.indexOfFirst { it.id == profile.id }
                    if (index < 0) return@runOnDefaultDispatcher
                    configurationList[index] = ProfileManager.getProfile(profile.id)!!
                    notifyItemChanged(index)
                }
            }

            override fun onRemoved(groupId: Long, profileId: Long) {
                if (groupId != proxyGroup.id) return
                runOnDefaultDispatcher {
                    val index = configurationList.indexOfFirst { it.id == profileId }
                    if (index < 0) return@runOnDefaultDispatcher
                    configurationList.removeAt(index)
                    if (profileId == DataStore.selectedProxy) {
                        if (configurationList.isNotEmpty()) {
                            DataStore.selectedProxy = configurationList[0].id
                        } else {
                            DataStore.selectedProxy = 0
                        }
                    }
                    onMainDispatcher {
                        notifyItemRemoved(index)
                    }
                }

            }

            override fun onCleared(groupId: Long) {
                if (groupId != proxyGroup.id) return
                configurationList.clear()
                notifyDataSetChanged()
            }

            override fun reloadProfiles(groupId: Long) {
                if (groupId != proxyGroup.id) return
                runOnDefaultDispatcher {
                    configurationList.clear()
                    configurationList.addAll(SagerDatabase.proxyDao.getByGroup(proxyGroup.id))
                    onMainDispatcher {
                        notifyDataSetChanged()
                    }
                }
            }

            fun refreshId(profileId: Long) {
                runOnDefaultDispatcher {
                    val index = configurationList.indexOfFirst { it.id == profileId }
                    if (index < 0) return@runOnDefaultDispatcher
                    onMainDispatcher {
                        notifyItemChanged(index)
                    }
                }
            }

        }

        override fun onDestroyView() {
            super.onDestroyView()

            undoManager.flush()
        }

        inner class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view) {

            val profileName: TextView = view.findViewById(R.id.profile_name)
            val profileType: TextView = view.findViewById(R.id.profile_type)
            val profileAddress: TextView = view.findViewById(R.id.profile_address)
            val trafficText: TextView = view.findViewById(R.id.traffic_text)
            val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
            val editButton: ImageView = view.findViewById(R.id.edit)
            lateinit var item: ProxyEntity

            fun bind(proxyEntity: ProxyEntity) {
                item = proxyEntity
                view.setOnClickListener {
                    runOnDefaultDispatcher {
                        if (DataStore.selectedProxy != proxyEntity.id) {
                            val lastSelected = DataStore.selectedProxy
                            DataStore.selectedProxy = proxyEntity.id
                            onMainDispatcher {
                                adapter.refreshId(lastSelected)
                                selectedView.visibility = View.VISIBLE
                            }
                        }
                    }
                }

                profileName.text = proxyEntity.displayName()
                profileType.text = proxyEntity.displayType()
                val showTraffic = proxyEntity.rx + proxyEntity.tx != 0L
                trafficText.isGone = !showTraffic
                if (showTraffic) {
                    trafficText.text = view.context.getString(R.string.traffic,
                        Formatter.formatFileSize(view.context, proxyEntity.rx),
                        Formatter.formatFileSize(view.context, proxyEntity.tx))
                }

                if (proxyEntity.requireBean().name.isNullOrBlank()) {
                    profileAddress.isGone = true
                } else {
                    profileAddress.isGone = false
                    val bean = proxyEntity.requireBean()
                    @SuppressLint("SetTextI18n")
                    profileAddress.text = "${bean.serverAddress}:${bean.serverPort}"
                }

                editButton.setOnClickListener {
                    it.context.startActivity(proxyEntity.settingIntent(it.context))
                }

                runOnDefaultDispatcher {
                    val selected = DataStore.selectedProxy == proxyEntity.id
                    onMainDispatcher {
                        selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    }

                }

            }

        }

    }


}
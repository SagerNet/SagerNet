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

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.fmt.shadowsocks.toUri
import io.nekohasekai.sagernet.fmt.shadowsocks.toV2rayN
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.toUri
import io.nekohasekai.sagernet.fmt.socks.toV2rayN
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.parcelize.Parcelize
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet


class ConfigurationFragment : ToolbarFragment(R.layout.group_list_main),
    Toolbar.OnMenuItemClickListener,
    PopupMenu.OnMenuItemClickListener {

    lateinit var adapter: GroupPagerAdapter
    lateinit var tabLayout: TabLayout
    lateinit var groupPager: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.inflateMenu(R.menu.profile_manager_menu)
        toolbar.setOnMenuItemClickListener(this)

        groupPager = view.findViewById(R.id.group_pager)
        tabLayout = view.findViewById(R.id.group_tab)
        adapter = GroupPagerAdapter()
        groupPager.adapter = adapter
        groupPager.offscreenPageLimit = 2

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

        toolbar.setOnClickListener {
            val fragment =
                (childFragmentManager.findFragmentByTag("f" + selectGroup.id) as GroupFragment?)

            if (fragment != null) {
                if (fragment.selected) {
                    val selectedProxy = DataStore.selectedProxy
                    val selectedProfileIndex =
                        fragment.adapter.configurationIdList.indexOf(selectedProxy)

                    val first =
                        (fragment.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                    val last =
                        (fragment.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

                    if (selectedProfileIndex !in first..last) {
                        fragment.configurationListView.scrollTo(selectedProfileIndex)
                        return@setOnClickListener
                    }
                }

                fragment.configurationListView.scrollTo(0)
            }
        }
    }

    val selectGroup get() = adapter.groupList[tabLayout.selectedTabPosition]
    fun snackbar(text: String) = (activity as MainActivity).snackbar(text)

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_clipboard -> {
                val text = SagerNet.getClipboardText()
                if (text.isBlank()) {
                    snackbar(getString(R.string.clipboard_empty)).show()
                } else runOnDefaultDispatcher {
                    val proxies = parseProxies(text)
                    if (proxies.isEmpty()) onMainDispatcher {
                        snackbar(getString(R.string.action_import_err)).show()
                    } else {
                        val selectGroupId = selectGroup.id
                        for (proxy in proxies) {
                            ProfileManager.createProfile(selectGroupId, proxy)
                        }
                        onMainDispatcher {
                            snackbar(requireContext().resources.getQuantityString(
                                R.plurals.added,
                                proxies.size,
                                proxies.size
                            )).show()
                        }
                    }
                }
            }
            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java).apply {
                    putExtra(ProfileSettingsActivity.EXTRA_GROUP_ID, selectGroup.id)
                })
            }
            R.id.action_new_ss -> {
                startActivity(Intent(requireActivity(),
                    ShadowsocksSettingsActivity::class.java).apply {
                    putExtra(ProfileSettingsActivity.EXTRA_GROUP_ID, selectGroup.id)
                })
            }
            R.id.action_from_link -> {
                AlertDialogFragment.setResultListener<SubDialogFragment, SubEditResult>(this) { _, ret ->
                    val result =
                        ret?.takeIf { !it.link.isNullOrEmpty() } ?: return@setResultListener
                    SubAddDialog().apply { arg(result);key() }.show(parentFragmentManager, null)
                }
                SubDialogFragment().apply { key() }.show(parentFragmentManager, null)
            }
        }
        return true
    }

    @Parcelize
    data class SubEditResult(val link: String?) : Parcelable
    class SubDialogFragment : AlertDialogFragment<Empty, SubEditResult>(),
        TextWatcher, AdapterView.OnItemSelectedListener {
        private lateinit var editText: EditText
        private lateinit var inputLayout: TextInputLayout
        private val positive by lazy { (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE) }

        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            val activity = requireActivity()

            @SuppressLint("InflateParams")
            val view = activity.layoutInflater.inflate(R.layout.dialog_subscription, null)
            editText = view.findViewById(R.id.content)
            inputLayout = view.findViewById(R.id.content_layout)
            editText.addTextChangedListener(this@SubDialogFragment)
            setTitle(R.string.add_subscription)
            setPositiveButton(android.R.string.ok, listener)
            setNegativeButton(android.R.string.cancel, null)
            setView(view)
        }

        override fun onStart() {
            super.onStart()
            validate()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) = validate(value = s)
        override fun onNothingSelected(parent: AdapterView<*>?) = check(false)
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
            validate()

        private fun validate(value: Editable = editText.text) {
            var message = ""
            positive.isEnabled = if (value.isBlank()) false else try {
                val url = value.toString().toHttpUrl()
                if ("http".equals(url.scheme, true)) message =
                    getString(R.string.cleartext_http_warning)
                true
            } catch (e: Exception) {
                message = e.readableMessage
                false
            }
            inputLayout.isErrorEnabled = true
            inputLayout.error = message
        }

        override fun ret(which: Int) = when (which) {
            DialogInterface.BUTTON_POSITIVE -> SubEditResult(editText.text.toString())
            DialogInterface.BUTTON_NEUTRAL -> SubEditResult(null)
            else -> null
        }

        override fun onClick(dialog: DialogInterface?, which: Int) {
            if (which != DialogInterface.BUTTON_NEGATIVE) super.onClick(dialog, which)
        }
    }

    class SubAddDialog : AlertDialogFragment<SubEditResult, Empty>() {

        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setView(layoutInflater.inflate(R.layout.layout_loading, null).apply {
                findViewById<TextView>(R.id.loadingText)?.also {
                    it.setText(R.string.fetching_subscription)
                }
            })
            setCancelable(false)
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
                .newCall(Request.Builder().url(arg.link!!).build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnMainDispatcher {
                            alert(e.readableMessage).show()
                            dismiss()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            val body = response.body?.string()
                            runOnMainDispatcher {
                                alert("HTTP ${response.code} ${body ?: ""}".trim()).show()
                                dismiss()
                            }
                            return
                        }
                        runOnDefaultDispatcher {
                            runCatching {
                                ProfileManager.createGroup(response)
                            }.onFailure {
                                onMainDispatcher {
                                    alert(it.readableMessage).show()
                                }
                            }
                            onMainDispatcher {
                                dismiss()
                            }
                        }

                    }
                })
        }

    }

    inner class GroupPagerAdapter : FragmentStateAdapter(this), ProfileManager.GroupListener {

        var selectedGroupIndex = 0
        var groupList: ArrayList<ProxyGroup> = ArrayList()

        init {
            ProfileManager.addListener(this)

            runOnDefaultDispatcher {
                groupList = ArrayList(SagerDatabase.groupDao.allGroups())
                if (groupList.isEmpty()) {
                    SagerDatabase.groupDao.createGroup(ProxyGroup(isDefault = true))
                    groupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }

                val selectedGroup = ProfileManager.getProfile(DataStore.selectedProxy)?.groupId
                    ?: selectedGroupIndex
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

        override fun onAdd(group: ProxyGroup) {
            if (groupList.all { it.isDefault }) tabLayout.post {
                tabLayout.visibility = View.VISIBLE
            }

            groupList.add(group)
            notifyItemInserted(groupList.size - 1)
            tabLayout.post { tabLayout.getTabAt(groupList.size - 1)?.select() }
        }

        override fun onAddFinish(size: Int) {
            snackbar(requireContext().resources.getQuantityString(
                R.plurals.added, size, size
            )).show()
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
            return inflater.inflate(R.layout.configurtion_list_main, container, false)
        }

        lateinit var undoManager: UndoSnackbarManager<ProxyEntity>
        lateinit var adapter: ConfigurationAdapter

        private val isEnabled get() = (activity as MainActivity).state.let { it.canStop || it == BaseService.State.Stopped }
        private fun isProfileEditable(id: Long) =
            (activity as MainActivity).state == BaseService.State.Stopped || id != DataStore.selectedProxy

        lateinit var layoutManager: RecyclerView.LayoutManager
        lateinit var configurationListView: RecyclerView

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            if (!::proxyGroup.isInitialized) return

            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            /*} else {
                layoutManager = StaggeredGridLayoutManager( 2, LinearLayout.VERTICAL)
            }*/
            configurationListView =
                view.findViewById<RecyclerView>(R.id.configuration_list).also {
                    it.layoutManager = when (proxyGroup.layout) {
                        else -> layoutManager
                    }
                }
            adapter = ConfigurationAdapter()
            configurationListView.adapter = adapter
            configurationListView.setItemViewCacheSize(20)

            undoManager =
                UndoSnackbarManager(activity as MainActivity, adapter::undo, adapter::commit)

            if (!proxyGroup.isSubscription) {

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
                        undoManager.remove(index to (viewHolder as ConfigurationHolder).entity)
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
                }).attachToRecyclerView(configurationListView)

            }

        }

        inner class ConfigurationAdapter : RecyclerView.Adapter<ConfigurationHolder>(),
            ProfileManager.Listener {

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

            init {
                reloadProfiles(proxyGroup.id)
                ProfileManager.addListener(this)
            }

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): ConfigurationHolder {
                return ConfigurationHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.layout_profile, parent, false)
                )
            }

            override fun getItemId(position: Int): Long {
                return configurationIdList[position]
            }

            override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) {
                holder.bind(getItemAt(position))
            }

            override fun getItemCount(): Int {
                return configurationIdList.size
            }

            private val updated = HashSet<ProxyEntity>()

            fun move(from: Int, to: Int) {
                val first = getItemAt(from)
                var previousOrder = first.userOrder
                val (step, range) = if (from < to) Pair(1, from until to) else Pair(-1,
                    to + 1 downTo from)
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

            fun commitMove() {
                updated.forEach { SagerDatabase.proxyDao.updateProxy(it) }
                updated.clear()
            }

            fun remove(pos: Int) {
                configurationIdList.removeAt(pos)
                notifyItemRemoved(pos)
            }

            fun undo(actions: List<Pair<Int, ProxyEntity>>) {
                for ((index, item) in actions) {
                    configurationList[item.id] = item
                    configurationIdList.add(index, item.id)
                    notifyItemInserted(index)
                }
            }

            fun commit(actions: List<Pair<Int, ProxyEntity>>) {
                val profiles = actions.map { it.second }
                runOnDefaultDispatcher {
                    for (entity in profiles) {
                        ProfileManager.deleteProfile(entity.groupId, entity.id)
                    }
                }
            }

            override fun onAdd(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return
                undoManager.flush()
                val pos = itemCount
                configurationList[profile.id] = profile
                configurationIdList.add(profile.id)
                notifyItemInserted(pos)
            }

            override fun onUpdated(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return
                runOnDefaultDispatcher {
                    val index = configurationIdList.indexOf(profile.id)
                    if (index < 0) return@runOnDefaultDispatcher
                    undoManager.flush()
                    configurationList[profile.id] = profile
                    configurationListView.post {
                        notifyItemChanged(index)
                    }
                }
            }

            override fun onUpdated(profileId: Long, trafficStats: TrafficStats) {
                runOnDefaultDispatcher {
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
            }

            override fun onRemoved(groupId: Long, profileId: Long) {
                if (groupId != proxyGroup.id) return
                runOnDefaultDispatcher {
                    val index = configurationIdList.indexOf(profileId)
                    if (index < 0) return@runOnDefaultDispatcher
                    configurationIdList.removeAt(index)
                    configurationList.remove(profileId)
                    configurationListView.post {
                        notifyItemRemoved(index)
                    }
                }

            }

            override fun onCleared(groupId: Long) {
                if (groupId != proxyGroup.id) return
                configurationList.clear()
                configurationList.clear()
                notifyDataSetChanged()
            }

            override fun reloadProfiles(groupId: Long) {
                if (groupId != proxyGroup.id) return

                runOnDefaultDispatcher {
                    configurationIdList.clear()
                    configurationIdList.addAll(SagerDatabase.proxyDao.getIdsByGroup(proxyGroup.id))

                    if (selected) {
                        val selectedProxy = DataStore.selectedProxy
                        val selectedProfileIndex = configurationIdList.indexOf(selectedProxy)

                        onMainDispatcher {
                            configurationListView.scrollTo(selectedProfileIndex)
                        }
                    }

                    onMainDispatcher {
                        notifyDataSetChanged()
                    }

                    if (configurationIdList.isEmpty() && proxyGroup.isDefault) {
                        ProfileManager.createProfile(groupId,
                            SOCKSBean.DEFAULT_BEAN.clone().apply {
                                name = "Local tunnel"
                            })
                    }


                    for (proxyEntity in SagerDatabase.proxyDao.getByGroup(proxyGroup.id)) {
                        configurationList[proxyEntity.id] = proxyEntity
                    }

                }
            }

            suspend fun refreshId(profileId: Long) {
                val index = configurationIdList.indexOf(profileId)
                if (index < 0) return
                configurationListView.post {
                    notifyItemChanged(index)
                }
            }

        }

        override fun onDestroyView() {
            super.onDestroyView()

            if (!::undoManager.isInitialized) return
            undoManager.flush()
        }

        inner class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view),
            PopupMenu.OnMenuItemClickListener {

            val profileName: TextView = view.findViewById(R.id.profile_name)
            val profileType: TextView = view.findViewById(R.id.profile_type)
            val profileAddress: TextView = view.findViewById(R.id.profile_address)
            val trafficText: TextView = view.findViewById(R.id.traffic_text)
            val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
            val editButton: ImageView = view.findViewById(R.id.edit)
            val shareButton: ImageView = view.findViewById(R.id.share)

            lateinit var entity: ProxyEntity

            fun bind(proxyEntity: ProxyEntity) {
                entity = proxyEntity

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
                trafficText.isGone = !showTraffic
                if (showTraffic) {
                    trafficText.text = view.context.getString(R.string.traffic,
                        Formatter.formatFileSize(view.context, tx),
                        Formatter.formatFileSize(view.context, rx))
                }
                //  (trafficText.parent as View).isGone = !showTraffic && proxyGroup.isSubscription

                editButton.isGone = proxyGroup.isSubscription
                if (!proxyGroup.isSubscription) {
                    editButton.setOnClickListener {
                        it.context.startActivity(proxyEntity.settingIntent(it.context))
                    }
                }

                shareButton.setOnClickListener {
                    val popup = PopupMenu(requireContext(), it)
                    when (proxyEntity.type) {
                        "socks" -> {
                            popup.menuInflater.inflate(R.menu.socks_share_menu, popup.menu)
                        }
                        "ss" -> {
                            popup.menuInflater.inflate(R.menu.shadowsocks_share_menu, popup.menu)
                        }
                    }
                    popup.setOnMenuItemClickListener(this)
                    popup.show()
                }

                runOnDefaultDispatcher {
                    val selected = DataStore.selectedProxy == proxyEntity.id
                    onMainDispatcher {
                        selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
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
                        R.id.action_qr_code_standard -> {
                            showCode(entity.requireSOCKS().toUri())
                        }
                        R.id.action_qr_code_v2rayn -> {
                            showCode(entity.requireSOCKS().toV2rayN())
                        }
                        R.id.action_export_clipboard_standard -> {
                            export(entity.requireSOCKS().toUri())
                        }
                        R.id.action_export_clipboard_v2rayn -> {
                            export(entity.requireSOCKS().toV2rayN())
                        }

                        // shadowsocks

                        R.id.action_ss_qr_code_standard -> {
                            showCode(entity.requireSS().toUri())
                        }
                        R.id.action_ss_qr_code_v2rayn -> {
                            showCode(entity.requireSS().toV2rayN())
                        }
                        R.id.action_ss_export_clipboard_standard -> {
                            export(entity.requireSS().toUri())
                        }
                        R.id.action_ss_export_clipboard_v2rayn -> {
                            export(entity.requireSS().toV2rayN())
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
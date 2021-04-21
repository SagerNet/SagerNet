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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.*
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
            (childFragmentManager.findFragmentByTag("f" + adapter.groupList[0].id) as? GroupFragment)?.layoutManager?.scrollToPosition(
                0)
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
                            runOnMainDispatcher {
                                alert("HTTP ${response.code}").show()
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

                onMainDispatcher {
                    notifyDataSetChanged()
                    val hideTab = groupList.size == 1 && groupList[0].isDefault
                    tabLayout.isGone = hideTab
                    toolbar.elevation = if (hideTab) 0F else dp2px(4).toFloat()
                }

                val selectedGroup =
                    SagerDatabase.proxyDao.getById(DataStore.selectedProxy)?.id
                        ?: selectedGroupIndex
                if (selectedGroup != 0L) {
                    val selectedIndex = groupList.indexOfFirst { it.id == selectGroup.id }
                    selectedGroupIndex = selectedIndex
                    tabLayout.post { tabLayout.getTabAt(selectedIndex)?.select() }
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
            configurationListView =
                view.findViewById<RecyclerView>(R.id.configuration_list).also {
                    it.layoutManager = when (proxyGroup.layout) {
                        else -> layoutManager
                    }
                }
            adapter = ConfigurationAdapter()
            configurationListView.adapter = adapter
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

            var configurationList: MutableList<ProxyEntity> = mutableListOf()

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
                configurationList.add(profile)
                notifyItemInserted(pos)
            }

            override fun onUpdated(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return
                undoManager.flush()
                runOnDefaultDispatcher {
                    val index = configurationList.indexOfFirst { it.id == profile.id }
                    if (index < 0) return@runOnDefaultDispatcher
                    configurationList[index] = profile
                    val holder = layoutManager.findViewByPosition(index)
                        ?.let { configurationListView.getChildViewHolder(it) } as ConfigurationHolder?
                    onMainDispatcher {
                        if (holder != null) {
                            holder.bind(profile)
                        } else {
                            notifyItemChanged(index)
                        }
                    }
                }
            }

            override fun onRemoved(groupId: Long, profileId: Long) {
                if (groupId != proxyGroup.id) return
                runOnDefaultDispatcher {
                    val index = configurationList.indexOfFirst { it.id == profileId }
                    if (index < 0) return@runOnDefaultDispatcher
                    configurationList.removeAt(index)
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
                    if (configurationList.isEmpty() && proxyGroup.isDefault) {
                        ProfileManager.createProfile(groupId,
                            SOCKSBean.DEFAULT_BEAN.clone().apply {
                                name = "Local tunnel"
                            })
                    }

                    if (selected) {

                        val selectedProxy = DataStore.selectedProxy
                        val selectedProfileIndex =
                            configurationList.indexOfFirst { it.id == selectedProxy }

                        configurationListView.post {
                            layoutManager.scrollToPosition(selectedProfileIndex)
                        }

                    }

                    onMainDispatcher {
                        notifyDataSetChanged()
                    }

                }
            }

            suspend fun refreshId(profileId: Long) {
                val index = configurationList.indexOfFirst { it.id == profileId }
                if (index < 0) return
                onMainDispatcher {
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
                            adapter.refreshId(lastSelected)
                            onMainDispatcher {
                                selectedView.visibility = View.VISIBLE
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

                profileAddress.text = ""

                /*if (proxyEntity.requireBean().name.isNullOrBlank()) {
                    profileAddress.isGone = true
                } else {
                    profileAddress.isGone = false
                    val bean = proxyEntity.requireBean()
                    @SuppressLint("SetTextI18n")
                    profileAddress.text = "${bean.serverAddress}:${bean.serverPort}"
                }*/

                editButton.setOnClickListener {
                    it.context.startActivity(proxyEntity.settingIntent(it.context))
                }

                shareButton.setOnClickListener {
                    val popup = PopupMenu(requireContext(), it)
                    popup.menuInflater.inflate(R.menu.socks_share_menu, popup.menu)
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
                    when (entity.type) {
                        "socks" -> {
                            val bean = this.entity.requireSOCKS()
                            when (item.itemId) {
                                R.id.action_qr_code_standard -> {
                                    showCode(bean.toUri())
                                }
                                R.id.action_qr_code_v2rayn -> {
                                    showCode(bean.toV2rayN())

                                }
                                R.id.action_export_clipboard_standard -> {
                                    export(bean.toUri())
                                }
                                R.id.action_export_clipboard_v2rayn -> {
                                    export(bean.toV2rayN())
                                }
                            }
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
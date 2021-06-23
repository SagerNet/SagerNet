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
import android.os.Bundle
import android.os.Parcelable
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.databinding.LayoutEditGroupBinding
import io.nekohasekai.sagernet.databinding.LayoutGroupItemBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.ListHolderListener
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet

class GroupFragment : ToolbarFragment(R.layout.layout_group), Toolbar.OnMenuItemClickListener {

    lateinit var activity: MainActivity
    lateinit var groupListView: RecyclerView
    lateinit var layoutManager: LinearLayoutManager
    lateinit var groupAdapter: GroupAdapter
    lateinit var undoManager: UndoSnackbarManager<ProxyGroup>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity() as MainActivity

        ViewCompat.setOnApplyWindowInsetsListener(view, ListHolderListener)
        toolbar.setTitle(R.string.menu_group)
        toolbar.inflateMenu(R.menu.add_group_menu)
        toolbar.setOnMenuItemClickListener(this)

        groupListView = view.findViewById(R.id.group_list)
        layoutManager = FixedLinearLayoutManager(groupListView)
        groupListView.layoutManager = layoutManager
        groupAdapter = GroupAdapter()
        ProfileManager.addListener(groupAdapter)
        groupListView.adapter = groupAdapter

        undoManager = UndoSnackbarManager(activity, groupAdapter)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
        ) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) = if (!(viewHolder as GroupHolder).proxyGroup.isDefault) {
                super.getSwipeDirs(recyclerView, viewHolder)
            } else 0

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = viewHolder.bindingAdapterPosition
                groupAdapter.remove(index)
                undoManager.remove(index to (viewHolder as GroupHolder).proxyGroup)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
            ): Boolean {
                groupAdapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                super.clearView(recyclerView, viewHolder)
                groupAdapter.commitMove()
            }
        }).attachToRecyclerView(groupListView)

    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_create_group -> createGroup(false)
            R.id.action_from_link -> createGroup(true)
        }
        return true
    }

    fun createGroup(isSubscription: Boolean) {
        EditGroupFragment().apply {
            arg(GroupInfo(isSubscription))
            key()
        }.show(parentFragmentManager, "create_group")
    }

    @Parcelize
    data class GroupInfo(
        val isSubscription: Boolean = false,
        val proxyGroup: ProxyGroup? = null,
    ) : Parcelable

    @Transient
    private val updating = ConcurrentHashMap<Long, Boolean>()

    private suspend fun updateSubscription(
        proxyGroup: ProxyGroup,
        onRefreshStarted: Runnable,
        refreshFinished: (success: Boolean) -> Unit,
    ) {
        synchronized(this) {
            if (updating[proxyGroup.id] == true) return
            updating[proxyGroup.id] = true
        }
        onRefreshStarted.run()

        fun onRefreshFinished(success: Boolean) {
            refreshFinished(success)
            updating[proxyGroup.id] = false
        }

        runOnDefaultDispatcher {
            createProxyClient().newCall(Request.Builder().url(proxyGroup.subscriptionLink).build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnMainDispatcher {
                            onRefreshFinished(false)
                            Logs.d("onFailure", e)

                            activity.snackbar(e.readableMessage).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Logs.d("onResponse: $response")

                        var (subType, proxies) = try {
                            ProfileManager.parseSubscription(
                                (response.body ?: error("Empty response")).string()
                            ) ?: error(getString(R.string.no_proxies_found))
                        } catch (e: Exception) {
                            Logs.w(e)
                            runOnMainDispatcher {
                                onRefreshFinished(false)
                                activity.snackbar(e.readableMessage).show()
                            }
                            return
                        }

                        val proxiesMap = LinkedHashMap<String, AbstractBean>()
                        for (proxy in proxies) {
                            var index = 0
                            var name = proxy.displayName()
                            while (proxiesMap.containsKey(name)) {
                                println("Exists name: $name")
                                index++
                                name = name.replace(" (${index - 1})", "")
                                name = "$name ($index)"
                                proxy.name = name
                            }
                            proxiesMap[proxy.displayName()] = proxy
                        }
                        proxies = proxiesMap.values.toList()

                        val exists = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
                        val duplicate = ArrayList<String>()
                        if (proxyGroup.deduplication) {

                            Logs.d("Before deduplication: ${proxies.size}")

                            val uniqueProxies = LinkedHashSet<AbstractBean>()
                            val uniqueNames = HashMap<AbstractBean, String>()
                            for (proxy in proxies) {
                                if (!uniqueProxies.add(proxy)) {
                                    val index = uniqueProxies.indexOf(proxy)
                                    if (uniqueNames.containsKey(proxy)) {
                                        val name = uniqueNames[proxy]!!.replace(" ($index)", "")
                                        if (name.isNotBlank()) {
                                            duplicate.add("$name ($index)")
                                            uniqueNames[proxy] = ""
                                        }
                                    }
                                    duplicate.add(proxy.displayName() + " ($index)")
                                } else {
                                    uniqueNames[proxy] = proxy.displayName()
                                }
                            }
                            uniqueProxies.retainAll(uniqueNames.keys)
                            proxies = uniqueProxies.toList()
                        }

                        Logs.d("New profiles: ${proxies.size}")

                        val nameMap = proxies.map { bean ->
                            bean.displayName() to bean
                        }.toMap()

                        Logs.d("Unique profiles: ${nameMap.size}")

                        val toDelete = ArrayList<ProxyEntity>()
                        val toReplace = exists.mapNotNull { entity ->
                            val name = entity.displayName()
                            if (nameMap.contains(name)) name to entity else let {
                                toDelete.add(entity)
                                null
                            }
                        }.toMap()

                        Logs.d("toDelete profiles: ${toDelete.size}")
                        Logs.d("toReplace profiles: ${toReplace.size}")

                        val toUpdate = ArrayList<ProxyEntity>()
                        val added = mutableListOf<String>()
                        val updated = mutableMapOf<String, String>()
                        val deleted = toDelete.map { it.displayName() }

                        var userOrder = 1L
                        var changed = toDelete.size
                        for ((name, bean) in nameMap.entries) {
                            if (toReplace.contains(name)) {
                                val entity = toReplace[name]!!
                                val existsBean = entity.requireBean()
                                existsBean.applyFeatureSettings(bean)
                                when {
                                    existsBean != bean -> {
                                        changed++
                                        entity.putBean(bean)
                                        toUpdate.add(entity)
                                        updated[entity.displayName()] = name

                                        Logs.d("Updated profile: $name")
                                    }
                                    entity.userOrder != userOrder -> {
                                        entity.putBean(bean)
                                        toUpdate.add(entity)
                                        entity.userOrder = userOrder

                                        Logs.d("Reordered profile: $name")
                                    }
                                    else -> {
                                        Logs.d("Ignored profile: $name")
                                    }
                                }
                            } else {
                                changed++
                                SagerDatabase.proxyDao.addProxy(ProxyEntity(
                                    groupId = proxyGroup.id, userOrder = userOrder
                                ).apply {
                                    putBean(bean)
                                })
                                added.add(name)
                                Logs.d("Inserted profile: $name")
                            }
                            userOrder++
                        }

                        SagerDatabase.proxyDao.updateProxy(* toUpdate.toTypedArray()).also {
                            Logs.d("Updated profiles: $it")
                        }

                        SagerDatabase.proxyDao.deleteProxy(* toDelete.toTypedArray()).also {
                            Logs.d("Deleted profiles: $it")
                        }

                        val existCount = SagerDatabase.proxyDao.countByGroup(proxyGroup.id).toInt()

                        if (existCount != proxies.size) {

                            Logs.e("Exist profiles: $existCount, new profiles: ${proxies.size}")

                        }

                        runBlocking {
                            ProfileManager.updateGroup(proxyGroup.apply {
                                lastUpdate = System.currentTimeMillis()
                                type = subType
                            })

                            ProfileManager.postReload(proxyGroup.id)
                            onMainDispatcher {
                                onRefreshFinished(true)

                                if (changed == 0 && duplicate.isEmpty()) {
                                    activity.snackbar(
                                        activity.getString(
                                            R.string.group_no_difference, proxyGroup.displayName()
                                        )
                                    ).show()
                                } else {
                                    activity.snackbar(
                                        activity.getString(
                                            R.string.group_updated, proxyGroup.name, changed
                                        )
                                    ).setAction(R.string.group_show_diff) {

                                        var status = ""
                                        if (added.isNotEmpty()) {
                                            status += activity.getString(
                                                R.string.group_added,
                                                added.joinToString("\n", postfix = "\n\n")
                                            )
                                        }
                                        if (updated.isNotEmpty()) {
                                            status += activity.getString(R.string.group_changed,
                                                updated.map { it }
                                                    .joinToString("\n", postfix = "\n\n") {
                                                        if (it.key == it.value) it.key else "${it.key} => ${it.value}"
                                                    })
                                        }
                                        if (deleted.isNotEmpty()) {
                                            status += activity.getString(
                                                R.string.group_deleted,
                                                deleted.joinToString("\n", postfix = "\n\n")
                                            )
                                        }
                                        if (duplicate.isNotEmpty()) {
                                            status += activity.getString(
                                                R.string.group_duplicate,
                                                duplicate.joinToString("\n", postfix = "\n\n")
                                            )
                                        }

                                        MaterialAlertDialogBuilder(activity).setTitle(
                                            app.getString(
                                                R.string.group_diff, proxyGroup.displayName()
                                            )
                                        ).setMessage(status.trim())
                                            .setPositiveButton(android.R.string.ok, null).show()
                                    }.show()
                                }
                            }
                        }
                    }
                })
        }
    }

    class EditGroupFragment : AlertDialogFragment<GroupInfo, Empty>() {

        lateinit var nameEditText: EditText
        lateinit var nameLayout: TextInputLayout

        lateinit var linkEditText: EditText
        lateinit var linkLayout: TextInputLayout

        lateinit var deduplicationCard: MaterialCardView
        lateinit var deduplication: MaterialCheckBox

        val positive by lazy { (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE) }

        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            val activity = requireActivity()

            val binding = LayoutEditGroupBinding.inflate(layoutInflater)
            val proxyGroup = arg.proxyGroup

            nameLayout = binding.groupNameLayout
            nameEditText = binding.groupName
            if (proxyGroup != null) {
                nameEditText.setText(proxyGroup.displayName())
            }

            nameEditText.addTextChangedListener {
                validate()
            }

            linkLayout = binding.groupLinksLayout
            if (!arg.isSubscription) {
                linkLayout.isGone = true
            } else {
                linkEditText = binding.groupSubscriptionLink
                if (proxyGroup != null) {
                    linkEditText.setText(proxyGroup.subscriptionLink)
                }
                linkEditText.addTextChangedListener {
                    validate()
                }
            }

            deduplicationCard = binding.deduplicationCard
            deduplication = binding.deduplication
            if (!arg.isSubscription) {
                deduplicationCard.isVisible = false
            }

            if (proxyGroup != null) {
                deduplication.isChecked = proxyGroup.deduplication
            }

            deduplicationCard.setOnClickListener {
                deduplication.performClick()
            }

            deduplication.setOnCheckedChangeListener { _, _ ->
                validate()
            }

            setTitle(
                if (arg.proxyGroup == null) {
                    if (!arg.isSubscription) {
                        R.string.group_create
                    } else {
                        R.string.group_create_subscription
                    }
                } else {
                    if (!arg.isSubscription) {
                        R.string.group_edit
                    } else {
                        R.string.group_edit_subscription
                    }
                }
            )

            setPositiveButton(android.R.string.ok, listener)
            setNegativeButton(android.R.string.cancel, null)

            if (proxyGroup != null && !proxyGroup.isDefault) {
                setNeutralButton(R.string.delete, listener)
            }

            setView(binding.root)
        }

        override fun onStart() {
            super.onStart()

            positive.isEnabled = false
        }

        fun validate() {
            var pass = true

            val name = nameEditText.text
            if (name.isBlank()) {
                pass = false
                nameLayout.isErrorEnabled = true
                nameLayout.error = getString(R.string.group_name_required)
            } else {
                nameLayout.isErrorEnabled = false
            }

            if (arg.isSubscription) {
                val link = linkEditText.text
                if (link.isNotBlank()) {
                    try {
                        val url = link.toString().toHttpUrl()
                        if ("http".equals(url.scheme, true)) {
                            linkLayout.error = getString(R.string.cleartext_http_warning)
                            linkLayout.isErrorEnabled = true
                        } else {
                            linkLayout.isErrorEnabled = false
                        }
                    } catch (e: Exception) {
                        linkLayout.error = e.readableMessage
                        linkLayout.isErrorEnabled = true
                    }
                } else {
                    linkLayout.isErrorEnabled = false
                    pass = false
                }
            }
            positive.isEnabled = pass
        }

        override fun onClick(dialog: DialogInterface?, which: Int) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                runOnDefaultDispatcher {
                    val proxyGroup =
                        arg.proxyGroup ?: ProxyGroup().apply { isSubscription = arg.isSubscription }

                    proxyGroup.name = nameEditText.text.toString()
                    if (proxyGroup.isSubscription) {
                        proxyGroup.subscriptionLink = linkEditText.text.toString()
                    }
                    proxyGroup.deduplication = deduplication.isChecked

                    if (arg.proxyGroup == null) {
                        ProfileManager.createGroup(proxyGroup)
                    } else {
                        ProfileManager.updateGroup(proxyGroup)
                    }
                }
            } else if (which == DialogInterface.BUTTON_NEUTRAL) {
                DeleteConfirmationDialogFragment().apply {
                    arg(GroupIdToDelete(arg.proxyGroup!!.id))
                    key()
                }.show(parentFragmentManager, "delete_group")
            }
        }
    }

    @Parcelize
    data class GroupIdToDelete(val groupId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<GroupIdToDelete, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.group_delete_confirm_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    ProfileManager.deleteGroup(arg.groupId)
                }
            }
            setNegativeButton(R.string.no, null)
        }
    }

    inner class GroupAdapter : RecyclerView.Adapter<GroupHolder>(), ProfileManager.GroupListener,
        UndoSnackbarManager.Interface<ProxyGroup> {

        val groupList = ArrayList<ProxyGroup>()

        suspend fun reload() {
            val groups = SagerDatabase.groupDao.allGroups()
            groupList.clear()
            groupList.addAll(groups)
            groupListView.post {
                notifyDataSetChanged()
            }
        }

        init {
            runOnDefaultDispatcher {
                reload()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupHolder {
            return GroupHolder(LayoutGroupItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: GroupHolder, position: Int) {
            holder.bind(groupList[position])
        }

        override fun getItemCount(): Int {
            return groupList.size
        }

        override fun getItemId(position: Int): Long {
            return groupList[position].id
        }

        private val updated = HashSet<ProxyGroup>()

        fun move(from: Int, to: Int) {
            val first = groupList[from]
            var previousOrder = first.userOrder
            val (step, range) = if (from < to) Pair(1, from until to) else Pair(
                -1, to + 1 downTo from
            )
            for (i in range) {
                val next = groupList[i + step]
                val order = next.userOrder
                next.userOrder = previousOrder
                previousOrder = order
                groupList[i] = next
                updated.add(next)
            }
            first.userOrder = previousOrder
            groupList[to] = first
            updated.add(first)
            notifyItemMoved(from, to)
        }

        fun commitMove() = runOnDefaultDispatcher {
            updated.forEach { SagerDatabase.groupDao.updateGroup(it) }
            updated.clear()
        }

        fun remove(index: Int) {
            groupList.removeAt(index)
            notifyItemRemoved(index)
        }

        override fun undo(actions: List<Pair<Int, ProxyGroup>>) {
            for ((index, item) in actions) {
                groupList.add(index, item)
                notifyItemInserted(index)
            }
        }

        override fun commit(actions: List<Pair<Int, ProxyGroup>>) {
            val groups = actions.map { it.second }.toTypedArray()
            runOnDefaultDispatcher {
                ProfileManager.deleteGroup(* groups)
            }
        }

        override suspend fun onAdd(group: ProxyGroup) {
            groupList.add(group)
            delay(300L)

            onMainDispatcher {
                undoManager.flush()
                notifyItemInserted(groupList.size - 1)

                if (group.isSubscription && group.lastUpdate == 0L) refreshSubscription(group)
            }
        }

        override suspend fun onRemoved(groupId: Long) {
            val index = groupList.indexOfFirst { it.id == groupId }
            if (index == -1) return
            onMainDispatcher {
                undoManager.flush()

                groupList.removeAt(index)
                notifyItemRemoved(index)
            }
        }

        override suspend fun onUpdated(group: ProxyGroup) {
            val index = groupList.indexOfFirst { it.id == group.id }
            if (index == -1) {
                reload()
                return
            }
            groupList[index] = group
            onMainDispatcher {
                undoManager.flush()

                notifyItemChanged(index)
            }
        }

        override suspend fun onUpdated(groupId: Long) {
            val index = groupList.indexOfFirst { it.id == groupId }
            if (index == -1) {
                reload()
                return
            }
            onMainDispatcher {
                notifyItemChanged(index)
            }
        }

        override suspend fun refreshSubscription(proxyGroup: ProxyGroup) {
            val index = groupAdapter.groupList.indexOfFirst { it.id == proxyGroup.id }
            val (first, end) = layoutManager.let { it.findFirstVisibleItemPosition() to it.findLastVisibleItemPosition() }
            if (index !in first..end) {
                groupListView.post {
                    groupListView.scrollTo(index, true)
                }
            }
            groupListView.post {
                runOnDefaultDispatcher {
                    for (i in 0 until 5) {
                        ((groupListView.findViewHolderForAdapterPosition(index) as? GroupHolder).also {
                            if (it == null) {
                                delay(1000L)
                            }
                        } ?: continue).refreshRunnable(true)
                        break
                    }
                }
            }
        }

        override suspend fun refreshSubscription(
            proxyGroup: ProxyGroup,
            onRefreshStarted: Runnable,
            onRefreshFinished: (success: Boolean) -> Unit,
        ) {
            updateSubscription(proxyGroup, onRefreshStarted, onRefreshFinished)
        }

    }

    override fun onDestroy() {
        if (::groupAdapter.isInitialized) {
            ProfileManager.removeListener(groupAdapter)
        }

        super.onDestroy()

        if (!::undoManager.isInitialized) return
        undoManager.flush()
    }

    inner class GroupHolder(binding: LayoutGroupItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        lateinit var proxyGroup: ProxyGroup
        val groupName = binding.groupName
        val groupStatus = binding.groupStatus
        val editButton = binding.edit
        val shareButton = binding.share
        val updateButton = binding.groupUpdate
        val subscriptionUpdateProgress = binding.subscriptionUpdateProgress
        var refreshing = false

        val refreshRunnable = { needGo: Boolean ->
            runOnDefaultDispatcher {
                var uVisible = false

                ProfileManager.groupIterator {
                    refreshSubscription(proxyGroup, {
                        refreshing = true
                        runOnMainDispatcher {
                            (groupName.parent as LinearLayout).apply {
                                setPadding(
                                    paddingLeft, paddingTop - dp2px(4), paddingRight, paddingBottom
                                )
                            }

                            subscriptionUpdateProgress.isVisible = true
                            updateButton.isInvisible = true
                            if (editButton.isVisible) {
                                uVisible = true
                                editButton.isVisible = false //    shareButton.isVisible = false
                            }
                        }
                    }, {
                        DataStore.selectedGroup = proxyGroup.id

                        runOnMainDispatcher {
                            (groupName.parent as LinearLayout).apply {
                                setPadding(
                                    paddingLeft, paddingTop + dp2px(4), paddingRight, paddingBottom
                                )
                            }

                            // shareButton.isVisible = true

                            if (needGo && it) {
                                activity.displayFragmentWithId(R.id.nav_configuration)
                            } else {
                                subscriptionUpdateProgress.isVisible = false
                                updateButton.isInvisible = false
                                if (uVisible) {
                                    editButton.isVisible = true
                                }
                            }

                            refreshing = false
                        }
                    })
                }
            }
        }

        fun bind(group: ProxyGroup) {
            proxyGroup = group

            itemView.setOnClickListener { }

            updateButton.isInvisible = !proxyGroup.isSubscription
            groupName.text = proxyGroup.displayName()

            editButton.setOnClickListener {

                EditGroupFragment().apply {
                    arg(GroupInfo(proxyGroup.isSubscription, group))
                    key()
                }.show(parentFragmentManager, "edit_group")

            }

            updateButton.setOnClickListener {
                refreshRunnable(false)
            }

            shareButton.isVisible = false

            runOnDefaultDispatcher {
                val size = SagerDatabase.proxyDao.countByGroup(proxyGroup.id)
                onMainDispatcher {
                    if (!proxyGroup.isSubscription) {
                        if (size == 0L) {
                            groupStatus.setText(R.string.group_status_empty)
                        } else {
                            groupStatus.text =
                                app.resources.getString(R.string.group_status_proxies, size)
                        }
                    } else {
                        if (size == 0L) {
                            groupStatus.setText(R.string.group_status_empty_subscription)
                        } else {
                            val date = Date(group.lastUpdate)
                            @Suppress("DEPRECATION") groupStatus.text = app.resources.getString(
                                R.string.group_status_proxies_subscription,
                                size,
                                "${date.month + 1} - ${date.date}"
                            )
                        }
                    }
                }

            }

        }
    }

}
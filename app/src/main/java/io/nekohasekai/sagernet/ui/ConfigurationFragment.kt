package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ui.configuration.GroupPagerAdapter

class ConfigurationFragment : ToolbarFragment(R.layout.group_list_main),
    Toolbar.OnMenuItemClickListener,
    PopupMenu.OnMenuItemClickListener {

    lateinit var adapter: GroupPagerAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.inflateMenu(R.menu.profile_manager_menu)
        toolbar.setOnMenuItemClickListener(this)

        val groupPager = view.findViewById<ViewPager2>(R.id.group_pager)
        val tabLayout = view.findViewById<TabLayout>(R.id.group_tab)
        adapter = GroupPagerAdapter(this)
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

        runOnIoDispatcher {
            adapter.reloadList {
                tabLayout.isGone = it
                toolbar.elevation = if (it) 0F else dp2px(4).toFloat()
            }
        }

    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return true
    }

}
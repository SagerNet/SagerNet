package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.github.zawadz88.materialpopupmenu.popupMenu
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ui.configuration.GroupPagerAdapter

class ConfigurationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.group_list_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val groupPager = view.findViewById<ViewPager2>(R.id.group_pager)
        val tabLayout = view.findViewById<TabLayout>(R.id.group_tab)
        val adapter = GroupPagerAdapter(this)
        groupPager.adapter = adapter

        TabLayoutMediator(tabLayout, groupPager) { tab, position ->
            tab.text = adapter.groupList[position].name
                .takeIf { !it.isNullOrBlank() } ?: getString(R.string.group_default)
            tab.view.setOnLongClickListener {
                popupMenu {
                    dropDownVerticalOffset = 100
                    if (position == 0) {
                        dropdownGravity = Gravity.TOP or Gravity.START
                        dropDownHorizontalOffset = dp2px(16)
                    } else if (position == adapter.itemCount - 1) {
                        dropdownGravity = Gravity.TOP or Gravity.END
                        dropDownHorizontalOffset = -dp2px(16)
                    }
                    section {
                        item {
                            icon = R.drawable.ic_action_dns
                            label = "Hello"
                        }
                        item {
                            icon = R.drawable.ic_action_lock
                            label = "Hello W0rld!!!!"
                        }
                        item {
                            icon = R.drawable.ic_action_description
                            label = "1145141919"
                        }
                        val group = adapter.groupList[position]
                        //if (!group.isDefault) {
                        item {
                            icon = R.drawable.ic_action_delete
                            label = "Delete Group"
                            callback = {
                                runOnIoDispatcher {
                                    SagerDatabase.groupDao.delete(group)
                                    adapter.reloadList()
                                }
                            }
                        }
                        //  }
                    }
                }.show(requireContext(), it)

                true
            }
        }.attach()

        adapter.reloadList()

    }

}
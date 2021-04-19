package io.nekohasekai.sagernet.ui.configuration

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.onMainDispatcher

class GroupPagerAdapter(
    activity: Fragment,
) : FragmentStateAdapter(activity) {

    var groupList: List<ProxyGroup> = listOf()

    suspend fun reloadList(hideTab: (hideTab: Boolean) -> Unit) {
        groupList = SagerDatabase.groupDao.allGroups()
        if (groupList.isEmpty()) {
            SagerDatabase.groupDao.createGroup(ProxyGroup(isDefault = true))
            groupList = SagerDatabase.groupDao.allGroups()
        }
        onMainDispatcher {
            notifyDataSetChanged()
            hideTab(groupList.size == 1 && groupList[0].isDefault)
        }
    }

    override fun getItemCount(): Int {
        return groupList.size
    }

    override fun createFragment(position: Int): Fragment {
        return GroupFragment(groupList[position])
    }

    override fun getItemId(position: Int): Long {
        return groupList[position].id
    }

    override fun containsItem(itemId: Long): Boolean {
        return groupList.any { it.id == itemId }
    }

}
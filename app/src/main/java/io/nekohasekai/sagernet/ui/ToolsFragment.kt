/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
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
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutToolsBinding
import io.nekohasekai.sagernet.ktx.isExpert

class ToolsFragment : ToolbarFragment(R.layout.layout_tools) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_tools)

        val tools = mutableListOf<NamedFragment>()
        tools.add(CloudflareFragment())

        if (BuildConfig.DEBUG || isExpert) tools.add(DebugFragment())

        val binding = LayoutToolsBinding.bind(view)
        binding.toolsPager.adapter = ToolsAdapter(tools)

        TabLayoutMediator(binding.toolsTab, binding.toolsPager) { tab, position ->
            tab.text = tools[position].name()
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.attach()
    }

    inner class ToolsAdapter(val tools: List<Fragment>) : FragmentStateAdapter(this) {

        override fun getItemCount() = tools.size

        override fun createFragment(position: Int) = tools[position]
    }

}
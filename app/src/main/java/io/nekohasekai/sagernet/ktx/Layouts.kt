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

package io.nekohasekai.sagernet.ktx

import android.graphics.Rect
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.ui.MainActivity

class FixedLinearLayoutManager(val recyclerView: RecyclerView) :
    LinearLayoutManager(recyclerView.context, RecyclerView.VERTICAL, false) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    private var listenerDisabled = false

    override fun scrollVerticallyBy(
        dx: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
        if (listenerDisabled) return scrollRange
        val activity = recyclerView.context as? MainActivity
        if (activity == null) {
            listenerDisabled = true
            return scrollRange
        }

        val overscroll = dx - scrollRange
        if (overscroll > 0) {
            val view =
                (recyclerView.findViewHolderForAdapterPosition(findLastVisibleItemPosition())
                    ?: return scrollRange).itemView
            val itemLocation = Rect().also { view.getGlobalVisibleRect(it) }
            val fabLocation = Rect().also { activity.binding.fab.getGlobalVisibleRect(it) }
            if (!itemLocation.contains(fabLocation.left, fabLocation.top) && !itemLocation.contains(fabLocation.right, fabLocation.bottom)) {
                return scrollRange
            }
            activity.binding.fab.apply {
                if (isShown) hide()
            }
        } else {
            /*val screen = Rect().also { activity.window.decorView.getGlobalVisibleRect(it) }
            val location = Rect().also { activity.stats.getGlobalVisibleRect(it) }
            if (screen.bottom < location.bottom) {
                return scrollRange
            }
            val height = location.bottom - location.top
            val mH = activity.stats.measuredHeight

            if (mH > height) {
                return scrollRange
            }*/

            activity.binding.fab.apply {
                if (!isShown) show()
            }
        }
        return scrollRange
    }

}

class FixedGridLayoutManager(val recyclerView: RecyclerView, spanCount: Int) :
    GridLayoutManager(recyclerView.context, spanCount) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    private var listenerDisabled = false

    override fun scrollVerticallyBy(
        dx: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
        if (listenerDisabled) return scrollRange
        val activity = recyclerView.context as? MainActivity
        if (activity == null) {
            listenerDisabled = true
            return scrollRange
        }

        val overscroll = dx - scrollRange
        if (overscroll > 0) {
            val view =
                (recyclerView.findViewHolderForAdapterPosition(findLastVisibleItemPosition())
                    ?: return scrollRange).itemView
            val itemLocation = Rect().also { view.getGlobalVisibleRect(it) }
            val fabLocation = Rect().also { activity.binding.fab.getGlobalVisibleRect(it) }
            if (!itemLocation.contains(fabLocation.left, fabLocation.top) && !itemLocation.contains(fabLocation.right, fabLocation.bottom)) {
                return scrollRange
            }
            activity.binding.fab.apply {
                if (isShown) hide()
            }
        } else {
            /*val screen = Rect().also { activity.window.decorView.getGlobalVisibleRect(it) }
            val location = Rect().also { activity.stats.getGlobalVisibleRect(it) }
            if (screen.bottom < location.bottom) {
                return scrollRange
            }
            val height = location.bottom - location.top
            val mH = activity.stats.measuredHeight

            if (mH > height) {
                return scrollRange
            }*/

            activity.binding.fab.apply {
                if (!isShown) show()
            }
        }
        return scrollRange
    }

}
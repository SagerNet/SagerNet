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

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.ui.MainActivity

class FixedLinearLayoutManager(val context: Context) :
    LinearLayoutManager(context, RecyclerView.VERTICAL, false) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    private var listenerDisabled = false
    private var suppression = true

    override fun scrollVerticallyBy(
        dx: Int, recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
        if (listenerDisabled) return scrollRange
        val activity = context as? MainActivity
        if (activity == null) {
            listenerDisabled = true
            return scrollRange
        }
        val overscroll = dx - scrollRange

        if (overscroll > 0) {
            if (activity.stats.behavior.hide) {
                activity.fab.apply {
                    if (isShown) {
                        if (suppression) {
                            suppression = false
                            return scrollRange
                        }
                        hide()
                        suppression = true
                    }
                }
            }
        } else {
            activity.fab.apply {
                if (!isShown) {
                    if (suppression) {
                        suppression = false
                        return scrollRange
                    }
                    show()
                    suppression = true
                }
            }
        }
        return scrollRange
    }


}

class FixedGridLayoutManager(val context: Context, spanCount: Int) :
    GridLayoutManager(context, spanCount) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    private var listenerDisabled = false
    private var suppression = true

    override fun scrollVerticallyBy(
        dx: Int, recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
        if (listenerDisabled) return scrollRange
        val activity = context as? MainActivity
        if (activity == null) {
            listenerDisabled = true
            return scrollRange
        }
        val overscroll = dx - scrollRange

        if (overscroll > 0) {
            if (activity.stats.behavior.hide) {
                activity.fab.apply {
                    if (isShown) {
                        if (suppression) {
                            suppression = false
                            return scrollRange
                        }
                        hide()
                        suppression = true
                    }
                }
            }
        } else {
            activity.fab.apply {
                if (!isShown) {
                    if (suppression) {
                        suppression = false
                        return scrollRange
                    }
                    show()
                    suppression = true
                }
            }
        }
        return scrollRange
    }

}
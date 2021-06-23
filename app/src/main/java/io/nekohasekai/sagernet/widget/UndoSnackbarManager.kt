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

package io.nekohasekai.sagernet.widget

import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity

/**
 * @param activity ThemedActivity.
 * //@param view The view to find a parent from.
 * @param undo Callback for undoing removals.
 * @param commit Callback for committing removals.
 * @tparam T Item type.
 */
class UndoSnackbarManager<in T>(
    private val activity: ThemedActivity,
    private val callback: Interface<T>,
) {

    interface Interface<in T> {
        fun undo(actions: List<Pair<Int, T>>)
        fun commit(actions: List<Pair<Int, T>>)
    }

    private val recycleBin = ArrayList<Pair<Int, T>>()
    private val removedCallback = object : Snackbar.Callback() {
        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
            if (last === transientBottomBar && event != DISMISS_EVENT_ACTION) {
                callback.commit(recycleBin)
                recycleBin.clear()
                last = null
            }
        }
    }

    private var last: Snackbar? = null

    fun remove(items: Collection<Pair<Int, T>>) {
        recycleBin.addAll(items)
        val count = recycleBin.size
        activity.snackbar(activity.resources.getQuantityString(R.plurals.removed, count, count))
            .apply {
                addCallback(removedCallback)
                setAction(R.string.undo) {
                    callback.undo(recycleBin.reversed())
                    recycleBin.clear()
                }
                last = this
                show()
            }
    }

    fun remove(vararg items: Pair<Int, T>) = remove(items.toList())

    fun flush() = last?.dismiss()
}

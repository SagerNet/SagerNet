package io.nekohasekai.sagernet.widget

import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.MainActivity

/**
 * @param activity MainActivity.
 * //@param view The view to find a parent from.
 * @param undo Callback for undoing removals.
 * @param commit Callback for committing removals.
 * @tparam T Item type.
 */
class UndoSnackbarManager<in T>(private val activity: MainActivity, private val undo: (List<Pair<Int, T>>) -> Unit,
                                commit: ((List<Pair<Int, T>>) -> Unit)? = null) {
    private val recycleBin = ArrayList<Pair<Int, T>>()
    private val removedCallback = object : Snackbar.Callback() {
        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
            if (last === transientBottomBar && event != DISMISS_EVENT_ACTION) {
                commit?.invoke(recycleBin)
                recycleBin.clear()
                last = null
            }
        }
    }

    private var last: Snackbar? = null

    fun remove(items: Collection<Pair<Int, T>>) {
        recycleBin.addAll(items)
        val count = recycleBin.size
        activity.snackbar(activity.resources.getQuantityString(R.plurals.removed, count, count)).apply {
            addCallback(removedCallback)
            setAction(R.string.undo) {
                undo(recycleBin.reversed())
                recycleBin.clear()
            }
            last = this
            show()
        }
    }

    fun remove(vararg items: Pair<Int, T>) = remove(items.toList())

    fun flush() = last?.dismiss()
}

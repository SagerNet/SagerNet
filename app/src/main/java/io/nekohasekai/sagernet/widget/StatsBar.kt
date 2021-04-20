package io.nekohasekai.sagernet.widget

import android.content.Context
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.google.android.material.bottomappbar.BottomAppBar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StatsBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.bottomAppBarStyle,
) :
    BottomAppBar(context, attrs, defStyleAttr) {
    private lateinit var statusText: TextView
    private lateinit var txText: TextView
    private lateinit var rxText: TextView
    private lateinit var behavior: Behavior
    override fun getBehavior(): Behavior {
        if (!this::behavior.isInitialized) behavior = object : Behavior() {
            override fun onNestedScroll(
                coordinatorLayout: CoordinatorLayout, child: BottomAppBar, target: View,
                dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
                type: Int, consumed: IntArray,
            ) {
                super.onNestedScroll(coordinatorLayout,
                    child,
                    target,
                    dxConsumed,
                    dyConsumed + dyUnconsumed,
                    dxUnconsumed,
                    0,
                    type,
                    consumed)
            }
        }
        return behavior
    }

    override fun setOnClickListener(l: OnClickListener?) {
        statusText = findViewById(R.id.status)
        txText = findViewById(R.id.tx)
        rxText = findViewById(R.id.rx)
        super.setOnClickListener(l)
    }

    private fun setStatus(text: CharSequence) {
        statusText.text = text
        TooltipCompat.setTooltipText(this, text)
    }

    fun changeState(state: BaseService.State) {
        val activity = context as MainActivity
        fun postWhenStarted(what: () -> Unit) = activity.lifecycleScope.launch(Dispatchers.Main) {
            delay(100L)
            activity.whenStarted { what() }
        }
        if ((state == BaseService.State.Connected).also { hideOnScroll = it }) {
            postWhenStarted {
                performShow()
            }
            /*  tester.status.observe(activity) {
                  it.retrieve(this::setStatus) { msg ->
                      activity.snackbar(msg).show()
                  }
              }*/
        } else {
            postWhenStarted {
                performHide()
            }
            updateTraffic(0, 0, 0, 0)
            /* tester.status.removeObservers(activity)
             if (state != BaseService.State.Idle) tester.invalidate()*/
            setStatus(context.getText(when (state) {
                BaseService.State.Connecting -> R.string.connecting
                BaseService.State.Stopping -> R.string.stopping
                else -> R.string.not_connected
            }))
        }
    }

    fun updateTraffic(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long) {
        txText.text = "▲ ${Formatter.formatFileSize(context, txTotal)} |  ${
            context.getString(R.string.speed,
                Formatter.formatFileSize(context, txRate))
        }"
        rxText.text = "▼ ${Formatter.formatFileSize(context, rxTotal)} |  ${
            context.getString(R.string.speed,
                Formatter.formatFileSize(context, rxRate))
        }"
    }

    fun testConnection() {

    }

}

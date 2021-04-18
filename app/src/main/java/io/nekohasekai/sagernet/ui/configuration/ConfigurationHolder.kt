package io.nekohasekai.sagernet.ui.configuration

import android.content.Intent
import android.text.format.Formatter
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ui.settings.SettingsActivity

class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view) {

    val profileName: TextView = view.findViewById(R.id.profile_name)
    val profileType: TextView = view.findViewById(R.id.profile_type)
    val trafficText: TextView = view.findViewById(R.id.traffic_text)
    val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
    val editButton: ImageView = view.findViewById(R.id.edit)

    fun bind(proxyEntity: ProxyEntity) {
        view.setOnClickListener {
            runOnIoDispatcher {
                if (DataStore.selectedProxy != proxyEntity.id) {
                    DataStore.selectedProxy = proxyEntity.id
                    onMainDispatcher {
                        bind(proxyEntity)
                    }
                }
            }
        }

        profileName.text = proxyEntity.requireBean().name
        profileType.text = proxyEntity.displayType()
        val showTraffic = proxyEntity.rx + proxyEntity.tx != 0L
        trafficText.isGone = !showTraffic
        if (showTraffic) {
            trafficText.text = view.context.getString(R.string.traffic,
                Formatter.formatFileSize(view.context, proxyEntity.rx),
                Formatter.formatFileSize(view.context, proxyEntity.tx))
        }

        editButton.setOnClickListener {
            it.context.startActivity(Intent(it.context, SettingsActivity::class.java).apply {
                putExtra("id", proxyEntity.id)
            })
        }

        runOnIoDispatcher {
            if (DataStore.selectedProxy == proxyEntity.id) {
                onMainDispatcher {
                    selectedView.visibility = View.VISIBLE
                }
            } else {
                onMainDispatcher {
                    selectedView.visibility = View.INVISIBLE
                }
            }
        }

    }

}
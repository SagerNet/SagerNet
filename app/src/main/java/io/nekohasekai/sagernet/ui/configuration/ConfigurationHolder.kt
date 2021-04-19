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
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.settings.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.settings.SocksSettingsActivity

class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view), EventListener {

    val profileName: TextView = view.findViewById(R.id.profile_name)
    val profileType: TextView = view.findViewById(R.id.profile_type)
    val profileAddress: TextView = view.findViewById(R.id.profile_address)
    val trafficText: TextView = view.findViewById(R.id.traffic_text)
    val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
    val editButton: ImageView = view.findViewById(R.id.edit)

    var profileId = 0L

    init {
        registerListener(EVENT_UPDATE_PROFILE, this)
    }

    override fun onEvent(eventId: Int, vararg args: Any) {
        if (args[0] != profileId) return
        runOnIoDispatcher {
            val profile = SagerDatabase.proxyDao.getById(profileId) ?: return@runOnIoDispatcher
            onMainDispatcher {
                bind(profile)
            }
        }
    }

    fun bind(proxyEntity: ProxyEntity) {
        profileId = proxyEntity.id

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

        profileName.text = proxyEntity.displayName()
        profileType.text = proxyEntity.displayType()
        val showTraffic = proxyEntity.rx + proxyEntity.tx != 0L
        trafficText.isGone = !showTraffic
        if (showTraffic) {
            trafficText.text = view.context.getString(R.string.traffic,
                Formatter.formatFileSize(view.context, proxyEntity.rx),
                Formatter.formatFileSize(view.context, proxyEntity.tx))
        }

        if (proxyEntity.requireBean().name.isNullOrBlank()) {
            profileAddress.isGone = true
        } else {
            profileAddress.isGone = false
            val bean = proxyEntity.requireBean()
            profileAddress.text = "${bean.serverAddress}:${bean.serverPort}"
        }

        editButton.setOnClickListener {
            it.context.startActivity(Intent(it.context, when (proxyEntity.type) {
                "socks" -> SocksSettingsActivity::class.java
                else -> throw IllegalArgumentException()
            }).apply {
                putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, proxyEntity.id)
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
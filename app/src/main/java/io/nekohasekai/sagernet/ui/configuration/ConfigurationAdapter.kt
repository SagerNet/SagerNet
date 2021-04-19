package io.nekohasekai.sagernet.ui.configuration

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.*

class ConfigurationAdapter(private val groupIdToQuery: Long) :
    RecyclerView.Adapter<ConfigurationHolder>(), EventListener {

    init {
        registerListener(EVENT_UPDATE_GROUP, this)
    }

    override fun onEvent(eventId: Int, vararg args: Any) {
        if (args[0] != groupIdToQuery) return
        reloadList()
    }

    var configurationList: List<ProxyEntity> = listOf()

    fun reloadList() {
        runOnIoDispatcher {
            configurationList = SagerDatabase.proxyDao.getByGroup(groupIdToQuery)
            if (configurationList.isEmpty() &&
                (SagerDatabase.groupDao.getById(groupIdToQuery)
                    ?: return@runOnIoDispatcher).isDefault
            ) {
                SagerDatabase.proxyDao.addProxy(ProxyEntity(
                    groupId = groupIdToQuery,
                    type = "socks",
                    socksBean = SOCKSBean().apply {
                        serverAddress = "127.0.0.1"
                        serverPort = 1080
                        name = "Hello W0rld!"
                    }
                ))
                configurationList = SagerDatabase.proxyDao.getByGroup(groupIdToQuery)
                DataStore.selectedProxy = configurationList[0].id
            }
            onMainDispatcher {
                notifyDataSetChanged()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigurationHolder {
        return ConfigurationHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.layout_profile, parent, false)
        )
    }

    override fun getItemId(position: Int): Long {
        return configurationList[position].id
    }

    override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) {
        holder.bind(configurationList[position])
    }

    override fun getItemCount(): Int {
        return configurationList.size
    }

}
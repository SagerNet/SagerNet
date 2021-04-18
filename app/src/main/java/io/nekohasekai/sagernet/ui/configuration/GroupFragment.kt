package io.nekohasekai.sagernet.ui.configuration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyGroup

class GroupFragment @JvmOverloads constructor(private val proxyGroup: ProxyGroup? = null) :
    Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.configurtion_list_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val configurationList = view.findViewById<RecyclerView>(R.id.configuration_list)
        if (proxyGroup == null) return

        val adapter = ConfigurationAdapter(proxyGroup.id)

        configurationList.layoutManager = when (proxyGroup.layout) {
            else -> LinearLayoutManager(view.context)
        }
        configurationList.adapter = adapter

        adapter.reloadList()

    }

}
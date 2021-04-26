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

package io.nekohasekai.sagernet.ui

import android.graphics.Color
import android.os.Bundle
import android.os.RemoteException
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceDataStore
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.IShadowsocksService
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.widget.ServiceButton
import io.nekohasekai.sagernet.widget.StatsBar
import rikka.material.app.MaterialActivity

class MainActivity : MaterialActivity(), SagerConnection.Callback,
    OnPreferenceDataStoreChangeListener {

    companion object {
        var stateListener: ((BaseService.State) -> Unit)? = null
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var fab: ServiceButton
    lateinit var stats: StatsBar
    lateinit var drawer: DrawerLayout
    lateinit var coordinator: CoordinatorLayout
    lateinit var navView: NavigationView
    lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_main)

        coordinator = findViewById(R.id.coordinator)

        fab = findViewById(R.id.fab)
        stats = findViewById(R.id.stats)
        drawer = findViewById(R.id.drawer_layout)

        navView = findViewById(R.id.nav_view)
        navController = findNavController(R.id.nav_host_fragment)

        fab.setOnClickListener { if (state.canStop) SagerNet.stopService() else connect.launch(null) }
        stats.setOnClickListener { if (state == BaseService.State.Connected) stats.testConnection() }

        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_configuration, R.id.nav_group, R.id.nav_settings, R.id.nav_about
        ), drawer)

        navView.setupWithNavController(navController)

        /* ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
             view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                 bottomMargin = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom +
                         resources.getDimensionPixelOffset(R.dimen.mtrl_bottomappbar_fab_bottom_margin)
             }
             insets
         }*/

        changeState(BaseService.State.Idle)
        connection.connect(this, this)
        DataStore.configurationStore.registerChangeListener(this)
    }

    var state = BaseService.State.Idle

    private fun changeState(
        state: BaseService.State,
        msg: String? = null,
        animate: Boolean = false,
    ) {
        fab.changeState(state, this.state, animate)
        stats.changeState(state)
        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show()
        this.state = state
        stateListener?.invoke(state)
    }

    fun snackbar(text: CharSequence = ""): Snackbar {
        return Snackbar.make(coordinator, text, Snackbar.LENGTH_LONG).apply {
            anchorView = fab
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg, true)
    }

    val connection = SagerConnection(true)
    override fun onServiceConnected(service: IShadowsocksService) = changeState(try {
        BaseService.State.values()[service.state]
    } catch (_: RemoteException) {
        BaseService.State.Idle
    })

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) {
        if (it) snackbar().setText(R.string.vpn_permission_denied).show()
    }

    override fun trafficUpdated(profileId: Long, stats: TrafficStats) {
        if (profileId != 0L) this@MainActivity.stats.updateTraffic(
            stats.txRate, stats.rxRate)
        runOnDefaultDispatcher {
            ProfileManager.postTrafficUpdated(profileId, stats)
        }
    }

    override fun trafficPersisted(profileId: Long) {
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(profileId)
        }
//        ProfilesFragment.instance?.onTrafficPersisted(profileId)
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.SERVICE_MODE -> {
                connection.disconnect(this)
                connection.connect(this, this)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connection.bandwidthTimeout = 500
    }

    override fun onStop() {
        connection.bandwidthTimeout = 0
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        DataStore.configurationStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

}

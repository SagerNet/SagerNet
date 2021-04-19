package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.os.RemoteException
import android.view.Menu
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceDataStore
import com.github.shadowsocks.aidl.IShadowsocksService
import com.github.shadowsocks.aidl.TrafficStats
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerApp
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.dp2pxf
import io.nekohasekai.sagernet.widget.ListHolderListener
import io.nekohasekai.sagernet.widget.ServiceButton
import io.nekohasekai.sagernet.widget.StatsBar


class MainActivity : AppCompatActivity(), SagerConnection.Callback,
    OnPreferenceDataStoreChangeListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var fab: ServiceButton
    lateinit var stats: StatsBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        snackbar = findViewById(R.id.snackbar)
        ViewCompat.setOnApplyWindowInsetsListener(snackbar, ListHolderListener)

        val appBarLayout: AppBarLayout = findViewById(R.id.appbar)
        val elevation = dp2pxf(4)

        fab = findViewById(R.id.fab)
        stats = findViewById(R.id.stats)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)

        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_configuration, R.id.nav_about
        ), drawerLayout)

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            appBarLayout.elevation = if (destination.id == R.id.nav_configuration) 0f else elevation
        }

        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom +
                        resources.getDimensionPixelOffset(R.dimen.mtrl_bottomappbar_fab_bottom_margin)
            }
            insets
        }

        fab.setOnClickListener { toggle() }
        stats.setOnClickListener { if (state == BaseService.State.Connected) stats.testConnection() }

        changeState(BaseService.State.Idle) // reset everything to init state

        connection.connect(this, this)
        DataStore.configurationStore.registerChangeListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.profile_manager_menu, menu)
        return true
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
        /*   ProfilesFragment.instance?.profilesAdapter?.notifyDataSetChanged()  // refresh button enabled state
           stateListener?.invoke(state)*/
    }

    lateinit var snackbar: CoordinatorLayout private set
    fun snackbar(text: CharSequence = "") =
        Snackbar.make(snackbar, text, Snackbar.LENGTH_LONG).apply {
            anchorView = fab
        }


    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg, true)
    }

    private fun toggle() {
        if (state.canStop) SagerApp.stopService() else connect.launch(null)
    }

    private val connection = SagerConnection(true)
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
        if (profileId == 0L) this@MainActivity.stats.updateTraffic(
            stats.txRate, stats.rxRate, stats.txTotal, stats.rxTotal)
        if (state != BaseService.State.Stopping) {
            (supportFragmentManager.findFragmentById(R.id.nav_configuration) as? ConfigurationFragment)
//                ?.onTrafficUpdated(profileId, stats)
        }
    }

    override fun trafficPersisted(profileId: Long) {
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
        connection.bandwidthTimeout = 1000
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

}
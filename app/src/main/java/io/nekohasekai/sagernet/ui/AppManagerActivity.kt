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

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.*
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.util.set
import androidx.core.view.ViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.ListHolderListener
import io.nekohasekai.sagernet.widget.ListListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.DexFile
import java.io.File
import java.util.*
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

class AppManagerActivity : AppCompatActivity() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: AppManagerActivity? = null
        private const val SWITCH = "switch"

        private var receiver: BroadcastReceiver? = null
        private var cachedApps: Map<String, PackageInfo>? = null
        private fun getCachedApps(pm: PackageManager) = synchronized(AppManagerActivity) {
            if (receiver == null) receiver = app.listenForPackageChanges {
                synchronized(AppManagerActivity) {
                    receiver = null
                    cachedApps = null
                }
                instance?.loadApps()
            }
            // Labels and icons can change on configuration (locale, etc.) changes, therefore they are not cached.
            val cachedApps = cachedApps ?: pm.getInstalledPackages(
                PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .filter {
                    when (it.packageName) {
                        app.packageName -> false
                        "android" -> true
                        else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                    }
                }
                .associateBy { it.packageName }
            this.cachedApps = cachedApps
            cachedApps
        }
    }

    private class ProxiedApp(
        private val pm: PackageManager, private val appInfo: ApplicationInfo,
        val packageName: String,
    ) {
        val name: CharSequence = appInfo.loadLabel(pm)    // cached for sorting
        val icon: Drawable get() = appInfo.loadIcon(pm)
        val uid get() = appInfo.uid
        val sys get() = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view),
        View.OnClickListener {
        private lateinit var item: ProxiedApp

        init {
            view.setOnClickListener(this)
        }

        fun bind(app: ProxiedApp) {
            item = app
            itemView.findViewById<ImageView>(R.id.itemicon).setImageDrawable(app.icon)
            itemView.findViewById<TextView>(R.id.title).text = app.name
            itemView.findViewById<TextView>(R.id.desc).text = "${app.packageName} (${app.uid})"
            itemView.findViewById<SwitchCompat>(R.id.itemcheck).isChecked = isProxiedApp(app)
        }

        fun handlePayload(payloads: List<String>) {
            if (payloads.contains(SWITCH)) itemView.findViewById<SwitchCompat>(R.id.itemcheck).isChecked =
                isProxiedApp(item)
        }

        override fun onClick(v: View?) {
            if (isProxiedApp(item)) proxiedUids.delete(item.uid) else proxiedUids[item.uid] = true
            DataStore.individual =
                apps.filter { isProxiedApp(it) }.joinToString("\n") { it.packageName }

            appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>(), Filterable,
        FastScrollRecyclerView.SectionedAdapter {
        var filteredApps = apps

        suspend fun reload() {
            apps = getCachedApps(packageManager).map { (packageName, packageInfo) ->
                coroutineContext[Job]!!.ensureActive()
                ProxiedApp(packageManager, packageInfo.applicationInfo, packageName)
            }.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) =
            holder.bind(filteredApps[position])

        override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                holder.handlePayload(payloads as List<String>)
                return
            }

            onBindViewHolder(holder, position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder =
            AppViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_apps_item, parent, false))

        override fun getItemCount(): Int = filteredApps.size

        private val filterImpl = object : Filter() {
            override fun performFiltering(constraint: CharSequence) = FilterResults().apply {
                var filteredApps = if (constraint.isEmpty()) apps else apps.filter {
                    it.name.contains(constraint, true) ||
                            it.packageName.contains(constraint, true) ||
                            it.uid.toString().contains(constraint)
                }
                if (!sysApps) filteredApps = filteredApps.filter { !it.sys }
                count = filteredApps.size
                values = filteredApps
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                @Suppress("UNCHECKED_CAST")
                filteredApps = results.values as List<ProxiedApp>
                notifyDataSetChanged()
            }
        }

        override fun getFilter(): Filter = filterImpl

        override fun getSectionName(position: Int): String {
            return filteredApps[position].name.firstOrNull()?.toString() ?: ""
        }

    }

    private val loading by lazy { findViewById<View>(R.id.loading) }
    private lateinit var toolbar: Toolbar
    private lateinit var bypassGroup: ChipGroup
    private lateinit var list: RecyclerView

    private lateinit var search: TextInputEditText
    private val proxiedUids = SparseBooleanArray()
    private var loader: Job? = null
    private var apps = emptyList<ProxiedApp>()
    private val appsAdapter = AppsAdapter()

    private val shortAnimTime by lazy {
        resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
    }

    private fun View.crossFadeFrom(other: View) {
        clearAnimation()
        other.clearAnimation()
        if (visibility == View.VISIBLE && other.visibility == View.GONE) return
        alpha = 0F
        visibility = View.VISIBLE
        animate().alpha(1F).duration = shortAnimTime
        other.animate().alpha(0F).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                other.visibility = View.GONE
            }
        }).duration = shortAnimTime
    }

    private fun initProxiedUids(str: String = DataStore.individual) {
        proxiedUids.clear()
        val apps = getCachedApps(packageManager)
        for (line in str.lineSequence()) proxiedUids[(apps[line] ?: continue).applicationInfo.uid] =
            true
    }

    private fun isProxiedApp(app: ProxiedApp) = proxiedUids[app.uid]

    @UiThread
    private fun loadApps() {
        loader?.cancel()
        loader = lifecycleScope.launchWhenCreated {
            loading.crossFadeFrom(list)
            val adapter = list.adapter as AppsAdapter
            withContext(Dispatchers.IO) { adapter.reload() }
            adapter.filter.filter(search.text?.toString() ?: "")
            list.crossFadeFrom(loading)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_apps)
        ListHolderListener.setup(this)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        if (!DataStore.proxyApps) {
            DataStore.proxyApps = true
        }

        bypassGroup = findViewById(R.id.bypassGroup)
        bypassGroup.check(if (DataStore.bypass) R.id.appProxyModeBypass else R.id.appProxyModeOn)
        bypassGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.appProxyModeDisable -> {
                    DataStore.proxyApps = false
                    finish()
                }
                R.id.appProxyModeOn -> DataStore.bypass = false
                R.id.appProxyModeBypass -> DataStore.bypass = true
            }
        }

        initProxiedUids()
        list = findViewById(R.id.list)
        ViewCompat.setOnApplyWindowInsetsListener(list, ListListener)
        list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        list.itemAnimator = DefaultItemAnimator()
        list.adapter = appsAdapter

        search = findViewById(R.id.search)
        search.addTextChangedListener {
            appsAdapter.filter.filter(it?.toString() ?: "")
        }

        val showSysApps = findViewById<Chip>(R.id.show_system_apps)
        showSysApps.setOnCheckedChangeListener { _, isChecked ->
            sysApps = isChecked
            appsAdapter.filter.filter(search.text?.toString() ?: "")
        }

        instance = this
        loadApps()
    }

    private var sysApps = false

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.per_app_proxy_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_china_apps -> {
                scanChinaApps()
                return true
            }
            R.id.action_export_clipboard -> {
                val success =
                    SagerNet.trySetPrimaryClip("${DataStore.bypass}\n${DataStore.individual}")
                Snackbar.make(list,
                    if (success) R.string.action_export_msg else R.string.action_export_err,
                    Snackbar.LENGTH_LONG).show()
                return true
            }
            R.id.action_import_clipboard -> {
                val proxiedAppString =
                    SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!proxiedAppString.isNullOrEmpty()) {
                    val i = proxiedAppString.indexOf('\n')
                    try {
                        val (enabled, apps) = if (i < 0) {
                            proxiedAppString to ""
                        } else proxiedAppString.substring(0, i) to proxiedAppString.substring(i + 1)
                        //bypassGroup.check(if (enabled.toBoolean()) R.id.btn_bypass else R.id.btn_on)
                        DataStore.individual = apps
                        Snackbar.make(list, R.string.action_import_msg, Snackbar.LENGTH_LONG).show()
                        initProxiedUids(apps)
                        appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
                        return true
                    } catch (_: IllegalArgumentException) {
                    }
                }
                Snackbar.make(list, R.string.action_import_err, Snackbar.LENGTH_LONG).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun scanChinaApps() {

        val text: TextView

        val dialog = AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.layout_loading, null).apply {
                text = findViewById(R.id.loadingText)
                text.setText(R.string.scanning)
            })
            .setCancelable(false)
            .show()

        val txt = text.text.toString()

        runOnDefaultDispatcher {
            val chinaApps = LinkedList<Pair<PackageInfo, String>>()
            val chinaRegex = ("(" + arrayOf(
                "com.tencent",
                "com.alibaba",
                "com.umeng",
                "com.qihoo",
                "com.ali",
                "com.alipay",
                "com.amap",
                "com.sina",
                "com.weibo",
                "com.vivo",
                "com.xiaomi",
                "com.huawei",
                "com.taobao",
                "com.iab"
            ).joinToString("|") + ").*").toRegex()

            val bypass = DataStore.bypass

            apps = getCachedApps(packageManager).map { (packageName, packageInfo) ->
                kotlin.coroutines.coroutineContext[Job]!!.ensureActive()
                ProxiedApp(packageManager, packageInfo.applicationInfo, packageName)
            }.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))

            scan@ for ((pkg, app) in getCachedApps(packageManager).entries) {
                if (!sysApps && app.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                    continue
                }

                val index =
                    appsAdapter.filteredApps.indexOfFirst { it.uid == app.applicationInfo.uid }
                var changed = false

                onMainDispatcher {
                    text.text = (txt + " " + app.packageName + "\n\n" +
                            chinaApps.map { it.second }.reversed().joinToString("\n", postfix = "\n")).trim()
                }

                val dex = File(app.applicationInfo.publicSourceDir)
                val zipFile = ZipFile(dex)
                var dexFile: DexFile

                for (entry in zipFile.entries()) {
                    if (entry.name.startsWith("classes") && entry.name.endsWith(".dex")) {
                        val input = zipFile.getInputStream(entry).readBytes()
                        dexFile = try {
                            DexBackedDexFile.fromInputStream(null, input.inputStream())
                        } catch (e: Exception) {
                            Logs.w(e)
                            break
                        }
                        for (clazz in dexFile.classes) {
                            val clazzName = clazz.type.substring(1, clazz.type.length - 1)
                                .replace("/", ".")
                                .replace("$", ".")

                            if (clazzName.matches(chinaRegex)) {
                                chinaApps.add(app to app.applicationInfo.loadLabel(packageManager)
                                    .toString())
                                zipFile.closeQuietly()

                                if (bypass) {
                                    changed = !proxiedUids[app.applicationInfo.uid]
                                    proxiedUids[app.applicationInfo.uid] = true
                                } else {
                                    proxiedUids.delete(app.applicationInfo.uid)
                                }

                                continue@scan
                            }
                        }
                    }
                }
                zipFile.closeQuietly()

                if (bypass) {
                    proxiedUids.delete(app.applicationInfo.uid)
                } else {
                    changed = !proxiedUids[index]
                    proxiedUids[app.applicationInfo.uid] = true
                }

            }

            DataStore.individual =
                apps.filter { isProxiedApp(it) }.joinToString("\n") { it.packageName }

            onMainDispatcher {
                appsAdapter.notifyDataSetChanged()

                dialog.dismiss()
            }

        }


    }

    override fun supportNavigateUpTo(upIntent: Intent) =
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    override fun onKeyUp(keyCode: Int, event: KeyEvent?) =
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (toolbar.isOverflowMenuShowing) toolbar.hideOverflowMenu() else toolbar.showOverflowMenu()
        } else super.onKeyUp(keyCode, event)

    override fun onDestroy() {
        instance = null
        loader?.cancel()
        super.onDestroy()
    }
}

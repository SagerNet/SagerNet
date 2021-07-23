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

package io.nekohasekai.sagernet.group

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.fmt.PluginEntry
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GroupInterfaceAdapter(val context: ThemedActivity) : GroupManager.Interface {

    override suspend fun confirm(message: String): Boolean {
        return suspendCoroutine {
            runOnMainDispatcher {
                MaterialAlertDialogBuilder(context).setTitle(R.string.confirm)
                    .setMessage(message)
                    .setPositiveButton(R.string.yes) { _, _ -> it.resume(true) }
                    .setNegativeButton(R.string.no) { _, _ -> it.resume(false) }
                    .setOnCancelListener { _ -> it.resume(false) }
                    .show()
            }
        }
    }

    override suspend fun onUpdateSuccess(
        group: ProxyGroup,
        changed: Int,
        added: List<String>,
        updated: Map<String, String>,
        deleted: List<String>,
        duplicate: List<String>,
        byUser: Boolean
    ) {
        if (changed == 0 && duplicate.isEmpty()) {
            if (byUser) context.snackbar(
                    context.getString(
                            R.string.group_no_difference, group.displayName()
                    )
            ).show()
        } else {
            context.snackbar(context.getString(R.string.group_updated, group.name, changed)).show()

            var status = ""
            if (added.isNotEmpty()) {
                status += context.getString(
                        R.string.group_added, added.joinToString("\n", postfix = "\n\n")
                )
            }
            if (updated.isNotEmpty()) {
                status += context.getString(R.string.group_changed,
                        updated.map { it }.joinToString("\n", postfix = "\n\n") {
                            if (it.key == it.value) it.key else "${it.key} => ${it.value}"
                        })
            }
            if (deleted.isNotEmpty()) {
                status += context.getString(
                        R.string.group_deleted, deleted.joinToString("\n", postfix = "\n\n")
                )
            }
            if (duplicate.isNotEmpty()) {
                status += context.getString(
                        R.string.group_duplicate, duplicate.joinToString("\n", postfix = "\n\n")
                )
            }

            onMainDispatcher {
                delay(1000L)

                MaterialAlertDialogBuilder(context).setTitle(
                        context.getString(
                                R.string.group_diff, group.displayName()
                        )
                ).setMessage(status.trim()).setPositiveButton(android.R.string.ok, null).show()
            }

        }

    }

    override suspend fun onUpdateFailure(group: ProxyGroup, message: String) {
        onMainDispatcher {
            context.snackbar(message).show()
        }
    }

    override suspend fun alert(message: String) {
        return suspendCoroutine {
            runOnMainDispatcher {
                MaterialAlertDialogBuilder(context).setTitle(R.string.ooc_warning)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> it.resume(Unit) }
                    .setOnCancelListener { _ -> it.resume(Unit) }
                    .show()
            }
        }
    }

    override suspend fun onRequiringPlugin(group: ProxyGroup, issuer: String, plugin: String) {
        val plugins = PluginManager.fetchPlugins()
        if (plugins.any { it.id == plugin }) return

        try {
            val pluginEntity = enumValueOf<PluginEntry>(plugin)

            MaterialAlertDialogBuilder(context).setTitle(R.string.missing_plugin)
                .setMessage(
                        context.getString(
                                R.string.profile_requiring_plugin,
                                issuer,
                                context.getString(pluginEntity.nameId)
                        )
                )
                .setPositiveButton(R.string.action_download) { _, _ ->
                    showDownloadDialog(pluginEntity)
                }
                .setNeutralButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.action_learn_more) { _, _ ->
                    context.launchCustomTab("https://sagernet.org/plugin/")
                }
        } catch (e: IllegalArgumentException) {
            val missingPluginMessage = context.getString(R.string.plugin_unknown, plugin)
            context.snackbar(missingPluginMessage).show()
        }

        throw CancellationException()
    }

    override suspend fun onRequiringShadowsocksPlugin(group: ProxyGroup, plugin: String) {
        val missingPluginMessage = context.getString(R.string.plugin_unknown, plugin)
        context.snackbar(missingPluginMessage).show()
    }

    private fun showDownloadDialog(pluginEntry: PluginEntry) {
        var index = 0
        var playIndex = -1
        var fdroidIndex = -1
        var downloadIndex = -1

        val items = mutableListOf<String>()
        if (pluginEntry.downloadSource.playStore) {
            items.add(context.getString(R.string.install_from_play_store))
            playIndex = index++
        }
        if (pluginEntry.downloadSource.fdroid) {
            items.add(context.getString(R.string.install_from_fdroid))
            fdroidIndex = index++
        }

        items.add(context.getString(R.string.download))
        downloadIndex = index

        MaterialAlertDialogBuilder(context).setTitle(pluginEntry.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    playIndex -> context.launchCustomTab("https://play.google.com/store/apps/details?id=${pluginEntry.packageName}")
                    fdroidIndex -> context.launchCustomTab("https://f-droid.org/packages/${pluginEntry.packageName}/")
                    downloadIndex -> context.launchCustomTab(pluginEntry.downloadLink)
                }
            }
            .show()
    }

}
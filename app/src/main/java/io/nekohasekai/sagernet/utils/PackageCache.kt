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

package io.nekohasekai.sagernet.utils

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.listenForPackageChanges
import io.nekohasekai.sagernet.ktx.readableMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PackageCache {

    lateinit var installPackages: Map<String, PackageInfo>
    lateinit var packageMap: Map<String, Int>
    lateinit var labelMap: Map<String, String>
    val uidMap = HashMap<Int, HashSet<String>>()
    val loaded = Mutex(true)

    fun register() {
        reload()
        app.listenForPackageChanges(false) {
            reload()
        }
        loaded.unlock()
    }

    fun reload() {
        installPackages = app.packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .filter {
                when (it.packageName) {
                    "android" -> true
                    else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                }
            }
            .associateBy { it.packageName }

        val installed = app.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        packageMap = installed.associate { it.packageName to it.uid }
        labelMap = installed.associate {
            it.packageName to try {
                it.loadLabel(app.packageManager).toString()
            } catch (e: Exception) {
                "[Load label error: ${e.readableMessage}]"
            }
        }
        uidMap.clear()
        for (info in installed) {
            val uid = info.uid
            uidMap.getOrPut(uid) { HashSet() }.add(info.packageName)
        }
    }

    operator fun get(uid: Int) = uidMap[uid]
    operator fun get(packageName: String) = packageMap[packageName]

    suspend fun awaitLoad() {
        if (::labelMap.isInitialized) {
            return
        }
        loaded.withLock {
            // just await
        }
    }

    fun awaitLoadSync() {
        if (::labelMap.isInitialized) {
            return
        }
        runBlocking {
            loaded.withLock {
                // just await
            }
        }
    }

}
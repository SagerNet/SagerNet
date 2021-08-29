/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.marshall
import io.nekohasekai.sagernet.ktx.unmarshall
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

@TargetApi(24)
object DirectBoot : BroadcastReceiver() {
    private val file = File(SagerNet.deviceStorage.noBackupFilesDir, "directBootProfile")
    private var registered = false

    fun getDeviceProfile(): ProxyEntity? = try {
        file.readBytes().unmarshall(::ProxyEntity)
    } catch (_: IOException) {
        null
    }

    fun clean() {
        file.delete()
        // File(SagerNet.deviceStorage.noBackupFilesDir, BaseService.CONFIG_FILE).delete()
    }

    /**
     * app.currentProfile will call this.
     */
    fun update(profile: ProxyEntity? = ProfileManager.getProfile(DataStore.selectedProxy)) =
        if (profile == null) clean()
        else file.writeBytes(profile.marshall())

    fun flushTrafficStats() {
        getDeviceProfile()?.also {
            runBlocking {
                if (it.dirty) ProfileManager.updateProfile(it)
            }
        }
        update()
    }

    fun listenForUnlock() {
        if (registered) return
        app.registerReceiver(this, IntentFilter(Intent.ACTION_BOOT_COMPLETED))
        registered = true
    }

    override fun onReceive(context: Context, intent: Intent) {
        flushTrafficStats()
        app.unregisterReceiver(this)
        registered = false
    }
}

/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.bg.proto

import android.os.Build
import android.provider.Settings
import io.nekohasekai.sagernet.bg.AbstractInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.app
import libcore.ApiInstance

class ApiInstance : AbstractInstance {

    lateinit var point: ApiInstance

    override fun launch() {
        var deviceName = Settings.Secure.getString(app.contentResolver, "bluetooth_name")
        if (deviceName.isNullOrBlank()) {
            deviceName = Build.DEVICE
            if (!deviceName.startsWith(Build.MANUFACTURER)) {
                deviceName = Build.MANUFACTURER + " " + deviceName
            }
        }
        point = ApiInstance(
            deviceName,
            DataStore.socksPort,
            DataStore.localDNSPort,
            DataStore.enableLog,
            DataStore.bypassLan
        )
        point.start()
    }

    override fun close() {
        if (::point.isInitialized) point.close()
    }
}
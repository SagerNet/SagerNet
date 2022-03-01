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

package io.nekohasekai.sagernet.bg

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.net.Network
import android.os.PowerManager
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import libcore.Libcore

class ProxyService : Service(),
    BaseService.Interface,
    LocalResolver {
    override val data = BaseService.Data(this)
    override val tag: String get() = "SagerNetProxyService"
    override fun createNotification(profileName: String): ServiceNotification =
        ServiceNotification(this, profileName, "service-proxy", true)

    @Volatile
    override var underlyingNetwork: Network? = null
    var upstreamInterfaceName: String? = null
    override suspend fun preInit() {
        DefaultNetworkListener.start(this) {
            SagerNet.reloadNetwork(it)
            underlyingNetwork = it

            SagerNet.connectivity.getLinkProperties(it)?.also { link ->
                val oldName = upstreamInterfaceName
                if (oldName != link.interfaceName) {
                    upstreamInterfaceName = link.interfaceName
                }
                if (oldName != null && upstreamInterfaceName != null && oldName != upstreamInterfaceName) {
                    Libcore.resetConnections()
                }
            }
        }
        Libcore.setLocalhostResolver(this)
    }

    override var wakeLock: PowerManager.WakeLock? = null

    @SuppressLint("WakelockTimeout")
    override fun acquireWakeLock() {
        wakeLock = SagerNet.power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sagernet:proxy")
            .apply { acquire() }
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun killProcesses() {
        Libcore.setLocalhostResolver(null)
        super.killProcesses()
        GlobalScope.launch(Dispatchers.Default) { DefaultNetworkListener.stop(this) }
    }

    override fun onBind(intent: Intent) = super.onBind(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        super<BaseService.Interface>.onStartCommand(intent, flags, startId)

    override fun onDestroy() {
        super.onDestroy()
        data.binder.close()
    }
}

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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.*
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher

class SagerConnection(private var listenForDeath: Boolean = false) : ServiceConnection,
    IBinder.DeathRecipient {
    companion object {
        val serviceClass
            get() = when (DataStore.serviceMode) {
                Key.MODE_PROXY -> ProxyService::class
                Key.MODE_VPN -> VpnService::class //   Key.MODE_TRANS -> TransproxyService::class
                else -> throw UnknownError()
            }.java
    }

    interface Callback {
        fun stateChanged(state: BaseService.State, profileName: String?, msg: String?)
        fun trafficUpdated(profileId: Long, stats: TrafficStats, isCurrent: Boolean) {}
        fun statsUpdated(stats: List<AppStats>) {}
        fun observatoryResultsUpdated(groupId: Long) {}

        fun profilePersisted(profileId: Long) {}
        fun missingPlugin(profileName: String, pluginName: String) {}
        fun routeAlert(type: Int, routeName: String) {}

        fun onServiceConnected(service: ISagerNetService)

        /**
         * Different from Android framework, this method will be called even when you call `detachService`.
         */
        fun onServiceDisconnected() {}
        fun onBinderDied() {}
    }

    private var connectionActive = false
    private var callbackRegistered = false
    private var callback: Callback? = null
    private val serviceCallback = object : ISagerNetServiceCallback.Stub() {
        override fun stateChanged(state: Int, profileName: String?, msg: String?) {
            val s = BaseService.State.values()[state]
            SagerNet.started = s.canStop
            val callback = callback ?: return
            runOnMainDispatcher {
                callback.stateChanged(s, profileName, msg)
            }
        }

        override fun trafficUpdated(profileId: Long, stats: TrafficStats, isCurrent: Boolean) {
            val callback = callback ?: return
            runOnMainDispatcher {
                callback.trafficUpdated(profileId, stats, isCurrent)
            }
        }

        override fun profilePersisted(profileId: Long) {
            val callback = callback ?: return
            runOnMainDispatcher { callback.profilePersisted(profileId) }
        }

        override fun missingPlugin(profileName: String, pluginName: String) {
            val callback = callback ?: return
            runOnMainDispatcher {
                callback.missingPlugin(profileName, pluginName)
            }
        }

        override fun statsUpdated(statsList: AppStatsList) {
            val callback = callback ?: return
            callback.statsUpdated(statsList.data)
        }

        override fun routeAlert(type: Int, routeName: String) {
            val callback = callback ?: return
            runOnMainDispatcher {
                callback.routeAlert(type, routeName)
            }
        }

        override fun observatoryResultsUpdated(groupId: Long) {
            val callback = callback ?: return
            runOnMainDispatcher {
                callback.observatoryResultsUpdated(groupId)
            }
        }
    }

    private var binder: IBinder? = null

    var bandwidthTimeout = 0L
        set(value) {
            try {
                if (value > 0) service?.startListeningForBandwidth(serviceCallback, value)
                else service?.stopListeningForBandwidth(serviceCallback)
            } catch (_: RemoteException) {
            }
            field = value
        }
    var trafficTimeout = 0L
        set(value) {
            try {
                if (value > 0) service?.startListeningForStats(serviceCallback, value)
                else service?.stopListeningForStats(serviceCallback)
            } catch (_: RemoteException) {
            }
            field = value
        }
    var service: ISagerNetService? = null

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        this.binder = binder
        val service = ISagerNetService.Stub.asInterface(binder)!!
        this.service = service
        try {
            if (listenForDeath) binder.linkToDeath(this, 0)
            check(!callbackRegistered)
            service.registerCallback(serviceCallback)
            callbackRegistered = true
            if (bandwidthTimeout > 0) service.startListeningForBandwidth(
                serviceCallback, bandwidthTimeout
            )
            if (trafficTimeout > 0) service.startListeningForStats(
                serviceCallback, trafficTimeout
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        callback!!.onServiceConnected(service)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        unregisterCallback()
        callback?.onServiceDisconnected()
        service = null
        binder = null
    }

    override fun binderDied() {
        service = null
        callbackRegistered = false
        callback?.also { runOnMainDispatcher { it.onBinderDied() } }
    }

    private fun unregisterCallback() {
        val service = service
        if (service != null && callbackRegistered) try {
            service.unregisterCallback(serviceCallback)
        } catch (_: RemoteException) {
        }
        callbackRegistered = false
    }

    fun connect(context: Context, callback: Callback) {
        if (connectionActive) return
        connectionActive = true
        check(this.callback == null)
        this.callback = callback
        val intent = Intent(context, serviceClass).setAction(Action.SERVICE)
        context.bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    fun disconnect(context: Context) {
        unregisterCallback()
        if (connectionActive) try {
            context.unbindService(this)
        } catch (_: IllegalArgumentException) {
        }   // ignore
        connectionActive = false
        if (listenForDeath) try {
            binder?.unlinkToDeath(this, 0)
        } catch (_: NoSuchElementException) {
        }
        binder = null
        try {
            service?.stopListeningForBandwidth(serviceCallback)
            service?.stopListeningForStats(serviceCallback)
        } catch (_: RemoteException) {
        }
        service = null
        callback = null
    }
}

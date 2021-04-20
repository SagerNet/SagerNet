package io.nekohasekai.sagernet.bg

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import io.nekohasekai.sagernet.aidl.IShadowsocksService
import io.nekohasekai.sagernet.aidl.IShadowsocksServiceCallback
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SagerConnection(private var listenForDeath: Boolean = false) : ServiceConnection,
    IBinder.DeathRecipient {
    companion object {
        val serviceClass
            get() = when (DataStore.serviceMode) {
                Key.MODE_PROXY -> ProxyService::class
                Key.MODE_VPN -> VpnService::class
                //   Key.MODE_TRANS -> TransproxyService::class
                else -> throw UnknownError()
            }.java
    }

    interface Callback {
        fun stateChanged(state: BaseService.State, profileName: String?, msg: String?)
        fun trafficUpdated(profileId: Long, stats: TrafficStats) {}
        fun trafficPersisted(profileId: Long) {}

        fun onServiceConnected(service: IShadowsocksService)

        /**
         * Different from Android framework, this method will be called even when you call `detachService`.
         */
        fun onServiceDisconnected() {}
        fun onBinderDied() {}
    }

    private var connectionActive = false
    private var callbackRegistered = false
    private var callback: Callback? = null
    private val serviceCallback = object : IShadowsocksServiceCallback.Stub() {
        override fun stateChanged(state: Int, profileName: String?, msg: String?) {
            val callback = callback ?: return
            GlobalScope.launch(Dispatchers.Main.immediate) {
                callback.stateChanged(BaseService.State.values()[state], profileName, msg)
            }
        }

        override fun trafficUpdated(profileId: Long, stats: TrafficStats) {
            val callback = callback ?: return
            GlobalScope.launch(Dispatchers.Main.immediate) {
                callback.trafficUpdated(profileId,
                    stats)
            }
        }

        override fun trafficPersisted(profileId: Long) {
            val callback = callback ?: return
            GlobalScope.launch(Dispatchers.Main.immediate) { callback.trafficPersisted(profileId) }
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
    var service: IShadowsocksService? = null

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        this.binder = binder
        val service = IShadowsocksService.Stub.asInterface(binder)!!
        this.service = service
        try {
            if (listenForDeath) binder.linkToDeath(this, 0)
            check(!callbackRegistered)
            service.registerCallback(serviceCallback)
            callbackRegistered = true
            if (bandwidthTimeout > 0) service.startListeningForBandwidth(serviceCallback,
                bandwidthTimeout)
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
        callback?.also { GlobalScope.launch(Dispatchers.Main.immediate) { it.onBinderDied() } }
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
        } catch (_: RemoteException) {
        }
        service = null
        callback = null
    }
}

package io.nekohasekai.sagernet.bg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.IShadowsocksService
import io.nekohasekai.sagernet.aidl.IShadowsocksServiceCallback
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.broadcastReceiver
import io.nekohasekai.sagernet.ktx.readableMessage
import kotlinx.coroutines.*
import java.net.URL
import java.net.UnknownHostException

class BaseService {

    enum class State(val canStop: Boolean = false) {
        /**
         * Idle state is only used by UI and will never be returned by BaseService.
         */
        Idle,
        Connecting(true),
        Connected(true),
        Stopping,
        Stopped,
    }

    interface ExpectedException
    class ExpectedExceptionWrapper(e: Exception) : Exception(e.localizedMessage, e),
        ExpectedException

    class Data internal constructor(private val service: Interface) {
        var state = State.Stopped
        var processes: GuardedProcessPool? = null
        var proxy: ProxyInstance? = null
        var notification: ServiceNotification? = null

        val closeReceiver = broadcastReceiver { _, intent ->
            when (intent.action) {
                Intent.ACTION_SHUTDOWN -> service.persistStats()
                Action.RELOAD -> service.forceLoad()
                else -> service.stopRunner()
            }
        }
        var closeReceiverRegistered = false

        val binder = Binder(this)
        var connectingJob: Job? = null

        fun changeState(s: State, msg: String? = null) {
            if (state == s && msg == null) return
            binder.stateChanged(s, msg)
            state = s
        }
    }

    class Binder(private var data: Data? = null) : IShadowsocksService.Stub(), CoroutineScope,
        AutoCloseable {
        private val callbacks = object : RemoteCallbackList<IShadowsocksServiceCallback>() {
            override fun onCallbackDied(callback: IShadowsocksServiceCallback?, cookie: Any?) {
                super.onCallbackDied(callback, cookie)
                stopListeningForBandwidth(callback ?: return)
            }
        }
        private val bandwidthListeners =
            mutableMapOf<IBinder, Long>()  // the binder is the real identifier
        override val coroutineContext = Dispatchers.Main.immediate + Job()
        private var looper: Job? = null

        override fun getState(): Int = (data?.state ?: State.Idle).ordinal
        override fun getProfileName(): String = data?.proxy?.profile?.requireBean()?.name ?: "Idle"

        override fun registerCallback(cb: IShadowsocksServiceCallback) {
            callbacks.register(cb)
        }

        private fun broadcast(work: (IShadowsocksServiceCallback) -> Unit) {
            val count = callbacks.beginBroadcast()
            try {
                repeat(count) {
                    try {
                        work(callbacks.getBroadcastItem(it))
                    } catch (_: RemoteException) {
                    } catch (e: Exception) {
                    }
                }
            } finally {
                callbacks.finishBroadcast()
            }
        }

        private suspend fun loop() {
            var lastQueryTime = 0L
            while (true) {
                val delayMs = bandwidthListeners.values.minOrNull()
                delay(delayMs ?: return)
                val queryTime = System.currentTimeMillis()
                val sinceLastQueryInSeconds = (queryTime - lastQueryTime) / 1000L
                val proxy = data?.proxy ?: continue
                lastQueryTime = queryTime
                val up = proxy.uplink
                val down = proxy.downlink
                if (up + down == 0L) continue
                val stats = TrafficStats(
                    up / sinceLastQueryInSeconds,
                    down / sinceLastQueryInSeconds,
                    proxy.uplinkTotal,
                    proxy.downlinkTotal
                )
                if (data?.state == State.Connected && bandwidthListeners.isNotEmpty()) {
                    broadcast { item ->
                        if (bandwidthListeners.contains(item.asBinder())) {
                            item.trafficUpdated(proxy.profile.id, stats)
                        }
                    }
                }

            }

        }

        override fun startListeningForBandwidth(
            cb: IShadowsocksServiceCallback,
            timeout: Long,
        ) {
            launch {
                if (bandwidthListeners.isEmpty() and (bandwidthListeners.put(cb.asBinder(),
                        timeout) == null)
                ) {
                    check(looper == null)
                    looper = launch { loop() }
                }
                if (data?.state != State.Connected) return@launch
                val data = data
                data?.proxy ?: return@launch
                val sum = TrafficStats()
                cb.trafficUpdated(0, sum)
            }
        }

        override fun stopListeningForBandwidth(cb: IShadowsocksServiceCallback) {
            launch {
                if (bandwidthListeners.remove(cb.asBinder()) != null && bandwidthListeners.isEmpty()) {
                    looper!!.cancel()
                    looper = null
                }
            }
        }

        override fun unregisterCallback(cb: IShadowsocksServiceCallback) {
            stopListeningForBandwidth(cb)   // saves an RPC, and safer
            callbacks.unregister(cb)
        }

        fun stateChanged(s: State, msg: String?) = launch {
            val profileName = profileName
            broadcast { it.stateChanged(s.ordinal, profileName, msg) }
        }

        fun trafficPersisted(ids: List<Long>) = launch {
            if (bandwidthListeners.isNotEmpty() && ids.isNotEmpty()) broadcast { item ->
                if (bandwidthListeners.contains(item.asBinder())) ids.forEach(item::trafficPersisted)
            }
        }

        override fun close() {
            callbacks.kill()
            cancel()
            data = null
        }
    }

    interface Interface {
        val data: Data
        val tag: String
        fun createNotification(profileName: String): ServiceNotification

        fun onBind(intent: Intent): IBinder? =
            if (intent.action == Action.SERVICE) data.binder else null

        fun forceLoad() {
            if (DataStore.selectedProxy == 0L) {
                stopRunner(false, (this as Context).getString(R.string.profile_empty))
            }
            val s = data.state
            when {
                s == State.Stopped -> startRunner()
                s.canStop -> stopRunner(true)
                else -> Logs.w("Illegal state $s when invoking use")
            }
        }

        val isVpnService get() = false

        suspend fun startProcesses() {
            data.proxy!!.start()
        }

        fun startRunner() {
            this as Context
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, javaClass))
            else startService(Intent(this, javaClass))
        }

        fun killProcesses(scope: CoroutineScope) {
            data.proxy?.stop()
            data.processes?.run {
                close(scope)
                data.processes = null
            }
        }

        fun stopRunner(restart: Boolean = false, msg: String? = null) {
            if (data.state == State.Stopping) return
            // channge the stated
            data.changeState(State.Stopping)
            GlobalScope.launch(Dispatchers.Main.immediate) {
                data.connectingJob?.cancelAndJoin() // ensure stop connecting first
                this@Interface as Service
                // we use a coroutineScope here to allow clean-up in parallel
                coroutineScope {
                    killProcesses(this)
                    // clean up receivers
                    val data = data
                    if (data.closeReceiverRegistered) {
                        unregisterReceiver(data.closeReceiver)
                        data.closeReceiverRegistered = false
                    }

                    data.notification?.destroy()
                    data.notification = null
                    data.proxy?.shutdown(this)
                    data.binder.trafficPersisted(listOfNotNull(data.proxy).map { it.profile.id })
                    data.proxy = null
                }

                // change the state
                data.changeState(State.Stopped, msg)

                // stop the service if nothing has bound to it
                if (restart) startRunner() else {
                    //   BootReceiver.enabled = false
                    stopSelf()
                }
            }
        }

        fun persistStats() =
            listOfNotNull(data.proxy).forEach { it.persistStats() }

        suspend fun preInit() {}
        suspend fun openConnection(url: URL) = url.openConnection()

        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val data = data
            if (data.state != State.Stopped) return Service.START_NOT_STICKY
            val profile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
            this as Context
            if (profile == null) {
                // gracefully shutdown: https://stackoverflow.com/q/47337857/2245107
                data.notification = createNotification("")
                stopRunner(false, getString(R.string.profile_empty))
                return Service.START_NOT_STICKY
            }
            val proxy = ProxyInstance(profile)
            data.proxy = proxy
            if (!data.closeReceiverRegistered) {
                registerReceiver(data.closeReceiver, IntentFilter().apply {
                    addAction(Action.RELOAD)
                    addAction(Intent.ACTION_SHUTDOWN)
                    addAction(Action.CLOSE)
                }, "$packageName.SERVICE", null)
                data.closeReceiverRegistered = true
            }

            data.notification = createNotification(profile.requireBean().name)

            data.changeState(State.Connecting)
            data.connectingJob = GlobalScope.launch(Dispatchers.Main) {
                try {
                    Executable.killAll()    // clean up old processes
                    preInit()
                    proxy.init(this@Interface)
                    data.processes = GuardedProcessPool {
                        Logs.w(it)
                        stopRunner(false, it.readableMessage)
                    }
                    startProcesses()
                    data.changeState(State.Connected)
                } catch (_: CancellationException) {
                    // if the job was cancelled, it is canceller's responsibility to call stopRunner
                } catch (_: UnknownHostException) {
                    stopRunner(false, getString(R.string.invalid_server))
                } catch (exc: Throwable) {
                    if (exc is ExpectedException) Logs.d(exc.readableMessage) else Logs.w(exc)
                    stopRunner(false,
                        "${getString(R.string.service_failed)}: ${exc.readableMessage}")
                } finally {
                    data.connectingJob = null
                }
            }
            return Service.START_NOT_STICKY
        }
    }

}
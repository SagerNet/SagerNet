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

package io.nekohasekai.sagernet.bg

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.text.TextUtils
import io.nekohasekai.sagernet.ktx.Logs
import java.io.File
import java.io.IOException

object Executable {
    const val SS_LOCAL = "libsslocal.so"
    const val TUN2SOCKS = "libtun2socks.so"

    private val EXECUTABLES = setOf(SS_LOCAL, TUN2SOCKS)

    fun killAll() {
        for (process in File("/proc").listFiles { _, name -> TextUtils.isDigitsOnly(name) }
            ?: return) {
            val exe = File(try {
                File(process, "cmdline").inputStream().bufferedReader().readText()
            } catch (_: IOException) {
                continue
            }.split(Character.MIN_VALUE, limit = 2).first())
            if (EXECUTABLES.contains(exe.name)) try {
                Os.kill(process.name.toInt(), OsConstants.SIGKILL)
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.ESRCH) {
                    Logs.w("SIGKILL ${exe.absolutePath} (${process.name}) failed")
                    Logs.w(e)
                }
            }
        }
    }
}

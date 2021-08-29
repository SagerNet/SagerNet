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

/***
 * If you modify and release but do not release the source code, you violate the GPL, so this is made.
 *
 * @author nekohasekai
 */
package io.nekohasekai.sagernet.ktx

import android.content.Context
import android.content.pm.PackageManager.GET_SIGNATURES
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import cn.hutool.crypto.digest.DigestUtil

val devKeys = arrayOf(
    "32250A4B5F3A6733DF57A3B9EC16C38D2C7FC5F2F693A9636F8F7B3BE3549641"
)

fun Context.getSignature(): Signature {
    val appInfo = packageManager.getPackageInfo(
        packageName, if (Build.VERSION.SDK_INT >= 28) GET_SIGNING_CERTIFICATES else GET_SIGNATURES
    )
    return if (Build.VERSION.SDK_INT >= 28) {
        appInfo.signingInfo.apkContentsSigners[0]
    } else {
        appInfo.signatures[0]
    }
}

fun Context.getSha256Signature(): String {
    return DigestUtil.sha256Hex(getSignature().toByteArray()).uppercase()
}

fun Context.isVerified(): Boolean {
    when (val s = getSha256Signature()) {
        in devKeys,
        -> return true
        else -> {
            Logs.w("Unknown signature: $s")
        }
    }
    return false
}

fun Context.checkMT() {
    val fuckMT = block {
        Thread.setDefaultUncaughtExceptionHandler(null)
        Thread.currentThread().uncaughtExceptionHandler = null
        try {
            Process.killProcess(Process.myPid())
        } catch (e: Exception) {
        }
        Runtime.getRuntime().exit(0)
    }

    try {
        Class.forName("bin.mt.apksignaturekillerplus.HookApplication")
        runOnMainDispatcher(fuckMT)
        return
    } catch (ignored: ClassNotFoundException) {
    }

    if (isVerified()) return

    val manifestMF = javaClass.getResourceAsStream("/META-INF/MANIFEST.MF")
    if (manifestMF == null) {
        Logs.w("/META-INF/MANIFEST.MF not found")
        return
    }

    val input = manifestMF.bufferedReader()
    val headers = input.use { (0 until 5).map { readLine() } }.joinToString("\n")

    // WTF version?
    if (headers.contains("Android Gradle 3.5.0")) {
        runOnMainDispatcher(fuckMT)
    }

}
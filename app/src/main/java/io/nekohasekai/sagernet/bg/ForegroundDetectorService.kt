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

package io.nekohasekai.sagernet.bg

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.utils.PackageCache
import libcore.Libcore

class ForegroundDetectorService : AccessibilityService() {

    class NotStartedException(val routeName: String) : IllegalStateException()

    val imeApps by lazy {
        (applicationContext.getSystemService(
            INPUT_METHOD_SERVICE
        ) as InputMethodManager).inputMethodList.map { it.packageName }
    }
    var fromIme = false

    override fun onCreate() {
        super.onCreate()

        Logs.i("Started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        val packageName = event.packageName?.takeIf { it.isNotBlank() }?.toString() ?: return
        if (packageName == "com.android.systemui") return
        if (packageName in imeApps) {
            val uid = PackageCache[packageName] ?: return
            PackageCache.awaitLoadSync()
            Libcore.setForegroundImeUid(uid)
            fromIme = true

            Logs.d("Foreground IME changed to ${event.packageName}/${event.className}: uid $uid")
            return
        }

        PackageCache.awaitLoadSync()
        var uid = PackageCache[packageName] ?: -1
        if (uid < 10000) {
            uid = 1000
        }

        Libcore.setForegroundUid(uid)
        if (fromIme) {
            Libcore.setForegroundImeUid(0)
            fromIme = false

            Logs.d("Foreground IME changed to none")
        }

        Logs.d("Foreground changed to ${event.packageName}/${event.className}: uid $uid")
    }

    override fun onInterrupt() {
        Logs.i("Interrupted")

        Libcore.setForegroundUid(0)
    }

}
/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.ui.sai

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder
import android.widget.Toast
import io.nekohasekai.sagernet.InstallerProvider
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs

class SplitAPKsInstallerService : Service() {

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Logs.d("Incoming $intent")
        when (val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -10)) {
            -10 -> {
                Logs.d("Bad intent: $intent")
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val userIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                userIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(userIntent)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(applicationContext, R.string.sai_succeed, Toast.LENGTH_LONG).show()
            }
            else -> {
                when (code) {
                    PackageInstaller.STATUS_FAILURE -> {
                        Toast.makeText(applicationContext, R.string.sai_error_generic, Toast.LENGTH_LONG).show()
                    }
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                        val blockedBy = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME) ?: getString(R.string.sai_error_blocked_device)
                        Toast.makeText(applicationContext, getString(R.string.sai_error_blocked, blockedBy), Toast.LENGTH_LONG).show()
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        Toast.makeText(applicationContext, R.string.sai_error_aborted, Toast.LENGTH_LONG).show()
                    }
                    PackageInstaller.STATUS_FAILURE_INVALID -> {
                        Toast.makeText(applicationContext, R.string.sai_error_invalid, Toast.LENGTH_LONG).show()
                    }
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                        Toast.makeText(applicationContext, R.string.sai_error_conflict, Toast.LENGTH_LONG).show()
                    }
                    PackageInstaller.STATUS_FAILURE_STORAGE -> {
                        Toast.makeText(applicationContext, R.string.sai_error_storage, Toast.LENGTH_LONG).show()
                    }
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                        Toast.makeText(applicationContext, R.string.sai_error_incompatible, Toast.LENGTH_LONG).show()
                    }
                }
                val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                if (sessionId != -1) {
                    try {
                        val useShizuku = DataStore.providerInstaller == InstallerProvider.SHIZUKU
                        val packageInstaller = if (useShizuku) ShizukuPackageManager.createPackageInstaller() else packageManager.packageInstaller
                        packageInstaller.abandonSession(sessionId)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

}
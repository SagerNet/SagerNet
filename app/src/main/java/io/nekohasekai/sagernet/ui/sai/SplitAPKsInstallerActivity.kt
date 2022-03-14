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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.InstallerProvider
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ui.ThemedActivity
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SplitAPKsInstallerActivity : ThemedActivity() {

    override val type = Type.Translucent

    lateinit var pendingUri: Uri
    val useShizuku by lazy { DataStore.providerInstaller == InstallerProvider.SHIZUKU }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        pendingUri = uri

        if (useShizuku) {
            Shizuku.addRequestPermissionResultListener(this::onRequestPermissionResult)

            val permission = try {
                Shizuku.checkSelfPermission()
            } catch (e: Exception) {
                Logs.w(e)
                shizukuError()
                return
            }

            if (permission != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
            } else {
                if (!Shizuku.pingBinder()) {
                    shizukuError()
                    return
                }
                handleUri()
            }
        } else {
            handleUri()
        }
    }

    private fun shizukuError() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
            .setMessage(R.string.shizuku_unavailable)
            .setPositiveButton(R.string.action_learn_more) { _, _ ->
                launchCustomTab("https://shizuku.rikka.app/")
                finish()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (useShizuku) {
            Shizuku.removeRequestPermissionResultListener(this::onRequestPermissionResult)
        }
    }

    private fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            handleUri()
        } else {
            finish()
        }
    }

    fun handleUri() {
        val document = getDocument(pendingUri)
        if (document == null) {
            returnError("Failed to get document")
            return
        }
        val documentName = document.name
        if (documentName == null) {
            returnError("Failed to get document name")
            return
        }
        Logs.d("Install $documentName")
        val extension = File(documentName).extension

        if (extension != "apks") {
            returnError("Not a split apks file")
            return
        }
        runCatching {
            contentResolver.openInputStream(pendingUri)!!.use {
                handleInputStream(it)
            }
        }.onFailure {
            Logs.w(it)
            returnError(it.readableMessage)
        }
    }

    fun returnError(message: String) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    fun handleInputStream(inputStream: InputStream) {
        val packageInstaller = if (useShizuku) ShizukuPackageManager.createPackageInstaller() else packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sessionParams.setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        if (useShizuku) {
            try {
                var installFlags = ShizukuPackageManager.getInstallFlags(sessionParams)
                installFlags = installFlags or (0x00000004 /*PackageManager.INSTALL_ALLOW_TEST*/ or 0x00000002) /*PackageManager.INSTALL_REPLACE_EXISTING*/
                ShizukuPackageManager.setInstallFlags(sessionParams, installFlags)
            } catch (e: Exception) {
                Logs.w("Failed to set install flags", e)
            }
        }
        val sessionId = try {
            packageInstaller.createSession(sessionParams)
        } catch (e: IllegalStateException) {
            packageInstaller.mySessions.forEach {
                packageInstaller.abandonSession(it.sessionId)
            }
            packageInstaller.createSession(sessionParams)
        }
        Logs.d("Create install session $sessionId")
        val session = if (!useShizuku) {
            packageInstaller.openSession(sessionId)
        } else {
            ShizukuPackageManager.openSession(sessionId)
        }
        ZipInputStream(inputStream).use { zip ->
            var currentZipEntry: ZipEntry
            while (true) {
                currentZipEntry = zip.nextEntry ?: break
                if (currentZipEntry.isDirectory) continue
                if (!currentZipEntry.name.lowercase().endsWith(".apk")) continue
                Logs.d("Write ${currentZipEntry.name}")
                session.openWrite(currentZipEntry.name, 0, currentZipEntry.size).use {
                    zip.copyTo(it)
                    session.fsync(it)
                }
            }
        }
        @SuppressLint("UnspecifiedImmutableFlag") val intent = PendingIntent.getService(
            applicationContext,
            0,
            Intent(applicationContext, SplitAPKsInstallerService::class.java),
            0
        )
        session.commit(intent.intentSender)
        Logs.d("Commit session")
        session.close()
        finish()
    }

    fun getDocument(contentUri: Uri): DocumentFile? {
        return if (ContentResolver.SCHEME_FILE == contentUri.scheme) {
            val path: String = contentUri.path ?: return null
            val file = File(path)
            if (file.isDirectory) null else DocumentFile.fromFile(file)
        } else {
            DocumentFile.fromSingleUri(this, contentUri)
        }
    }
}
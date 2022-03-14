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
import android.content.Context
import android.content.pm.*
import android.os.Build
import android.os.Process
import io.nekohasekai.sagernet.ktx.app
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuPackageManager {

    fun createPackageInstaller(): PackageInstaller {
        val isRoot = Shizuku.getUid() == 0
        return createPackageInstaller(
            packageInstaller,
            if (isRoot) app.packageName else "com.android.shell",
            Process.myUserHandle().hashCode()
        )
    }

    fun openSession(sessionId: Int) : PackageInstaller.Session {
        val binder = ShizukuBinderWrapper(packageInstaller.openSession(sessionId).asBinder())
        val session = IPackageInstallerSession.Stub.asInterface(binder)
        return PackageInstaller.Session::class.java.getConstructor(
            IPackageInstallerSession::class.java
        ).newInstance(session)
    }

    @SuppressLint("PrivateApi")
    fun getInstallFlags(params: PackageInstaller.SessionParams): Int {
        return PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")[params] as Int
    }

    @SuppressLint("PrivateApi")
    fun setInstallFlags(params: PackageInstaller.SessionParams, newValue: Int) {
        PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")[params] = newValue
    }

    private val packageManager by lazy {
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
        IPackageManager.Stub.asInterface(binder)
    }

    private val packageInstaller by lazy {
        IPackageInstaller.Stub.asInterface(ShizukuBinderWrapper(packageManager.packageInstaller.asBinder()))
    }

    private fun createPackageInstaller(
        installer: IPackageInstaller, installerPackageName: String, userId: Int
    ): PackageInstaller {
        return if (Build.VERSION.SDK_INT >= 26) {
            PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java, String::class.java, Int::class.javaPrimitiveType
            ).newInstance(installer, installerPackageName, userId)
        } else {
            PackageInstaller::class.java.getConstructor(
                Context::class.java,
                PackageManager::class.java,
                IPackageInstaller::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(
                app, app.packageManager, installer, installerPackageName, userId
            )
        }
    }


}
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

package io.nekohasekai.sagernet.plugin

import android.content.pm.ComponentInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.plugin.PluginManager.loadString

abstract class ResolvedPlugin(protected val resolveInfo: ResolveInfo) : Plugin() {
    protected abstract val componentInfo: ComponentInfo

    override val id by lazy { componentInfo.loadString(PluginContract.METADATA_KEY_ID)!! }
    override val version by lazy {
        SagerNet.application.getPackageInfo(componentInfo.packageName).versionCode
    }
    override val versionName by lazy {
        SagerNet.application.getPackageInfo(componentInfo.packageName).versionName
    }
    override val label: CharSequence get() = resolveInfo.loadLabel(SagerNet.application.packageManager)
    override val icon: Drawable get() = resolveInfo.loadIcon(SagerNet.application.packageManager)
    override val packageName: String get() = componentInfo.packageName
    override val directBootAware get() = Build.VERSION.SDK_INT < 24 || componentInfo.directBootAware
}

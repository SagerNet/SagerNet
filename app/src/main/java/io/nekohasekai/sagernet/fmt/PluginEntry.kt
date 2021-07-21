/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <sekai@neko.services>                    *
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

package io.nekohasekai.sagernet.fmt

import androidx.annotation.StringRes
import io.nekohasekai.sagernet.R

enum class PluginEntry(
    val pluginId: String,
    @StringRes val nameId: Int,
    val packageName: String,
    val downloadSource: DownloadSource = DownloadSource(),
    val downloadLink: String = "https://sagernet.org/download/"
) {
    TrojanGo("trojan-go", R.string.action_trojan_go, "io.nekohasekai.sagernet.plugin.trojan_go"),
    NaiveProxy("naive", R.string.action_naive, "io.nekohasekai.sagernet.plugin.naive"),
    PingTunnel("pingtunnel", R.string.action_ping_tunnel, "io.nekohasekai.sagernet.plugin.pingtunnel"),
    RelayBaton("relaybaton", R.string.action_relay_baton, "io.nekohasekai.sagernet.plugin.relaybaton"),
    Brook("brook", R.string.action_brook, "io.nekohasekai.sagernet.plugin.brook");

    data class DownloadSource(
        val playStore: Boolean = false, val fdroid: Boolean = false, val link: Boolean = false
    )

}
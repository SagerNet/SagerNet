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

package io.nekohasekai.sagernet.group

import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import java.util.*

abstract class GroupUpdater {

    abstract suspend fun doUpdate(proxyGroup: ProxyGroup)

    companion object {

        val updating = Collections.synchronizedSet<Long>(mutableSetOf())

        fun startUpdate(proxyGroup: ProxyGroup) {
            if (!updating.add(proxyGroup.id)) return
            runOnDefaultDispatcher {
                GroupManager.postUpdated(proxyGroup.id)

                val subscription = proxyGroup.subscription!!

                when (subscription.type) {
                    SubscriptionType.RAW -> RawUpdater.doUpdate(proxyGroup)
                    SubscriptionType.OOCv1 -> OpenOnlineConfigUpdater.doUpdate(proxyGroup)
                    SubscriptionType.SIP008 -> OpenOnlineConfigUpdater.doUpdate(proxyGroup)
                }
            }
        }

    }

}
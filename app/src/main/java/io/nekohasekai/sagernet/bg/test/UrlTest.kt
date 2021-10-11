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

package io.nekohasekai.sagernet.bg.test

import io.nekohasekai.sagernet.bg.proto.SSHInstance
import io.nekohasekai.sagernet.bg.proto.ShadowsocksInstance
import io.nekohasekai.sagernet.bg.proto.ShadowsocksRInstance
import io.nekohasekai.sagernet.bg.proto.SnellInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import libcore.Libcore

class UrlTest {

    val link = DataStore.connectionTestURL
    val timeout = 5000

    suspend fun doTest(profile: ProxyEntity): Int {
        if (profile.useClashBased()) {
            val instance = when (profile.type) {
                ProxyEntity.TYPE_SS -> ShadowsocksInstance(profile.ssBean!!, 0)
                ProxyEntity.TYPE_SSR -> ShadowsocksRInstance(profile.ssrBean!!, 0)
                ProxyEntity.TYPE_SNELL -> SnellInstance(profile.snellBean!!, 0)
                ProxyEntity.TYPE_SSH -> SSHInstance(profile.sshBean!!, 0)
                else -> error("unexpected")
            }
            instance.createInstance()
            return Libcore.urlTestClashBased(instance.instance, link, timeout).toInt()
        }

        return V2RayTestInstance(profile, link, timeout).doTest()
    }

}
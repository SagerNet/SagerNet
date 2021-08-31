/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.bg.ClashBasedInstance
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import libcore.Libcore

class SSHInstance(val server: SSHBean, val socksPort: Int) : ClashBasedInstance() {

    override fun createInstance() {

        instance = Libcore.newSSHInstance(
            socksPort,
            server.finalAddress,
            server.finalPort,
            server.username,
            server.authType,
            server.password,
            server.privateKey,
            server.privateKeyPassphrase,
            server.publicKey
        )

    }

}
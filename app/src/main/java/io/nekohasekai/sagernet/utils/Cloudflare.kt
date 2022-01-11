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

package io.nekohasekai.sagernet.utils

import com.wireguard.crypto.KeyPair
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.utils.cf.DeviceResponse
import io.nekohasekai.sagernet.utils.cf.RegisterRequest
import io.nekohasekai.sagernet.utils.cf.UpdateDeviceRequest
import libcore.Libcore
import okhttp3.OkHttp
import okhttp3.Request

// kang from wgcf
object Cloudflare {

    private const val API_URL = "https://api.cloudflareclient.com"
    private const val API_VERSION = "v0a1922"

    private const val CLIENT_VERSION_KEY = "CF-Client-Version"
    private const val CLIENT_VERSION = "a-6.3-1922"

    fun makeWireGuardConfiguration(): WireGuardBean {
        val keyPair = KeyPair()
        val client = Libcore.newHttpClient().apply {
            pinnedTLS12()
            trySocks5(DataStore.socksPort)
        }

        try {
            val response = client.newRequest().apply {
                setMethod("POST")
                setURL("$API_URL/$API_VERSION/reg")
                setHeader(CLIENT_VERSION_KEY, CLIENT_VERSION)
                setHeader("Accept", "application/json")
                setHeader("Content-Type", "application/json")
                setContentString(RegisterRequest.newRequest(keyPair.publicKey))
                setUserAgent("okhttp/3.12.1")
            }.execute()

            val device = gson.fromJson(response.contentString, DeviceResponse::class.java)
            val accessToken = device.token

            client.newRequest().apply {
                setMethod("PATCH")
                setURL(API_URL + "/" + API_VERSION + "/reg/" + device.id + "/account/reg/" + device.id)
                setHeader("Accept", "application/json")
                setHeader("Content-Type", "application/json")
                setHeader("Authorization", "Bearer $accessToken")
                setHeader(CLIENT_VERSION_KEY, CLIENT_VERSION)
                setContentString(UpdateDeviceRequest.newRequest())
                setUserAgent("okhttp/3.12.1")
            }.execute()

            val peer = device.config.peers[0]
            val localAddresses = device.config.interfaceX.addresses
            return WireGuardBean().apply {
                name = "CloudFlare Warp ${device.account.id}"
                privateKey = keyPair.privateKey.toBase64()
                peerPublicKey = peer.publicKey
                serverAddress = peer.endpoint.host.substringBeforeLast(":")
                serverPort = peer.endpoint.host.substringAfterLast(":").toInt()
                localAddress = localAddresses.v4 + "\n" + localAddresses.v6
            }
        } finally {
            client.close()
        }
    }

}
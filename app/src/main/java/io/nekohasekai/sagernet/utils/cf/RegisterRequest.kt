/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git></contact-git>@sekai.icu>                  *
 * *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                       *
 * *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 * *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http:></http:>//www.gnu.org/licenses/>.       *
 * *
 */
package io.nekohasekai.sagernet.utils.cf

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.wireguard.crypto.Key
import java.text.SimpleDateFormat
import java.util.*

data class RegisterRequest(
    @SerializedName("fcm_token") var fcmToken: String = "",
    @SerializedName("install_id") var installedId: String = "",
    var key: String = "",
    var locale: String = "",
    var model: String = "",
    var tos: String = "",
    var type: String = ""
) {

    companion object {
        fun newRequest(publicKey: Key): String {
            val request = RegisterRequest()
            request.fcmToken = ""
            request.installedId = ""
            request.key = publicKey.toBase64()
            request.locale = "en_US"
            request.model = "PC"
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'000000'+08:00", Locale.US)
            request.tos = format.format(Date())
            request.type = "Android"
            return Gson().toJson(request)
        }
    }
}
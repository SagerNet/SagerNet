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

package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.addTextChangedListener
import cn.hutool.core.util.CharUtil
import cn.hutool.json.JSONObject
import com.google.android.material.textfield.TextInputLayout
import com.takisoft.preferencex.EditTextPreference
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.readableMessage
import okhttp3.HttpUrl.Companion.toHttpUrl

class OOCv1TokenPreference : EditTextPreference {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(
        context: Context?, attrs: AttributeSet?, defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    constructor(
        context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)


    init {
        dialogLayoutResource = R.layout.layout_link_dialog

        setOnBindEditTextListener { editText ->
            editText.isSingleLine = false
            val linkLayout = editText.rootView.findViewById<TextInputLayout>(R.id.input_layout)

            fun validate() {
                if (editText.text.isBlank()) {
                    linkLayout.isErrorEnabled = false
                    return
                }
                var isValid = true
                try {
                    val tokenObject = JSONObject(editText.text)
                    val version = tokenObject.getInt("version")
                    if (version != 1) {
                        isValid = false
                        if (version != null) {
                            linkLayout.error = "Unsupported OOC version $version"
                        } else {
                            linkLayout.error = "Missing field: version"
                        }
                    }
                    if (isValid) {
                        val baseUrl = tokenObject.getStr("baseUrl")
                        when {
                            baseUrl.isNullOrBlank() -> {
                                linkLayout.error = "Missing field: baseUrl"
                                isValid = false
                            }
                            baseUrl.endsWith("/") -> {
                                linkLayout.error = "baseUrl must not contain a trailing slash"
                                isValid = false
                            }
                            !baseUrl.startsWith("https://") -> {
                                isValid = false
                                linkLayout.error = "Protocol scheme must be https"
                            }
                            else -> try {
                                baseUrl.toHttpUrl()
                            } catch (e: Exception) {
                                isValid = false
                                linkLayout.error = e.readableMessage
                            }
                        }
                    }
                    if (isValid && tokenObject.getStr("secret").isNullOrBlank()) {
                        isValid = false
                        linkLayout.error = "Missing field: secret"
                    }
                    if (isValid && tokenObject.getStr("userId").isNullOrBlank()) {
                        isValid = false
                        linkLayout.error = "Missing field: userId"
                    }
                    if (isValid) {
                        val certSha256 = tokenObject.getStr("certSha256")
                        if (!certSha256.isNullOrBlank()) {
                            when {
                                certSha256.length != 64 -> {
                                    isValid = false
                                    linkLayout.error =
                                        "certSha256 must be a SHA-256 hexadecimal string"
                                }
                                !certSha256.all { CharUtil.isLetterLower(it) || CharUtil.isNumber(it) } -> {
                                    isValid = false
                                    linkLayout.error =
                                        "certSha256 must be a hexadecimal string with lowercase letters"
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    isValid = false
                    linkLayout.error = e.readableMessage
                }

                linkLayout.isErrorEnabled = !isValid
            }
            validate()
            editText.addTextChangedListener {
                validate()
            }
        }

    }

}
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

package io.nekohasekai.sagernet.ui.profile

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import cn.hutool.json.JSONObject
import com.blacksquircle.ui.editorkit.listener.OnChangeListener
import com.blacksquircle.ui.editorkit.model.ColorScheme
import com.blacksquircle.ui.editorkit.theme.EditorTheme
import com.blacksquircle.ui.editorkit.widget.TextProcessor
import com.blacksquircle.ui.feature.editor.customview.ExtendedKeyboard
import com.blacksquircle.ui.language.base.model.SyntaxScheme
import com.blacksquircle.ui.language.json.JsonLanguage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.takisoft.preferencex.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.config.ConfigBean
import io.nekohasekai.sagernet.ktx.loadColor
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage

class ConfigSettingsActivity :
    ProfileSettingsActivity<ConfigBean>(R.layout.layout_config_settings) {

    override fun createEntity() = ConfigBean()

    var config = ""
    var dirty = false

    override fun ConfigBean.init() {
        DataStore.profileName = name
        config = content
    }

    override fun ConfigBean.serialize() {
        name = DataStore.profileName
        content = config
    }

    override suspend fun saveAndExit() {
        try {
            JSONObject(config)
        } catch (e: Exception) {
            onMainDispatcher {
                MaterialAlertDialogBuilder(this@ConfigSettingsActivity)
                    .setTitle(R.string.error_title)
                    .setMessage(e.readableMessage)
                    .show()
            }
            return
        }

        super.saveAndExit()
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.name_preferences)
    }

    fun mkTheme(): ColorScheme {
        val colorPrimary = loadColor(R.attr.colorPrimary)
        val colorPrimaryDark = loadColor(R.attr.colorPrimaryDark)
        EditorTheme.DARCULA

        return ColorScheme(
            textColor = colorPrimary,
            backgroundColor = Color.WHITE,
            gutterColor = colorPrimary,
            gutterDividerColor = Color.WHITE,
            gutterCurrentLineNumberColor = Color.WHITE,
            gutterTextColor = Color.WHITE,
            selectedLineColor = Color.parseColor("#D3D3D3"),
            selectionColor = colorPrimary,
            suggestionQueryColor = Color.parseColor("#7CE0F3"),
            findResultBackgroundColor = Color.parseColor("#5F5E5A"),
            delimiterBackgroundColor = Color.parseColor("#5F5E5A"),
            syntaxScheme = SyntaxScheme(
                numberColor = Color.parseColor("#BB8FF8"),
                operatorColor = Color.BLACK,
                keywordColor = Color.parseColor("#EB347E"),
                typeColor = Color.parseColor("#7FD0E4"),
                langConstColor = Color.parseColor("#EB347E"),
                preprocessorColor = Color.parseColor("#EB347E"),
                variableColor = Color.parseColor("#7FD0E4"),
                methodColor = Color.parseColor("#B6E951"),
                stringColor = colorPrimaryDark,
                commentColor = Color.parseColor("#89826D"),
                tagColor = Color.parseColor("#F8F8F8"),
                tagNameColor = Color.parseColor("#EB347E"),
                attrNameColor = Color.parseColor("#B6E951"),
                attrValueColor = Color.parseColor("#EBE48C"),
                entityRefColor = Color.parseColor("#BB8FF8")
            )
        )
    }

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.setTitle(R.string.config_settings)

        val editor = findViewById<TextProcessor>(R.id.editor)
        val extendedKeyboard = findViewById<ExtendedKeyboard>(R.id.extended_keyboard)
        extendedKeyboard.setKeyListener { char -> editor.insert(char) }
        extendedKeyboard.setHasFixedSize(true)
        extendedKeyboard.submitList("{}();,.=|&![]<>+-/*?:_".map { it.toString() })
        extendedKeyboard.setBackgroundColor(loadColor(R.attr.colorPrimary))
    }

    override fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
        val editor = findViewById<TextProcessor>(R.id.editor)
        editor.colorScheme = mkTheme()
        editor.language = JsonLanguage()
        editor.setTextContent(config)
        editor.onChangeListener = OnChangeListener {
            config = editor.text.toString()
            if (!dirty) {
                dirty = true
                DataStore.dirty = true
            }
        }
        editor.setHorizontallyScrolling(true)
    }

}
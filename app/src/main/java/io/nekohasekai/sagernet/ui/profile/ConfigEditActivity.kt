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

package io.nekohasekai.sagernet.ui.profile

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import cn.hutool.json.JSONObject
import com.blacksquircle.ui.editorkit.listener.OnChangeListener
import com.blacksquircle.ui.editorkit.model.ColorScheme
import com.blacksquircle.ui.feature.editor.customview.ExtendedKeyboard
import com.blacksquircle.ui.language.base.model.SyntaxScheme
import com.blacksquircle.ui.language.json.JsonLanguage
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutEditConfigBinding
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.ThemedActivity

class ConfigEditActivity : ThemedActivity() {

    var config = ""
    var dirty = false

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                (requireActivity() as ConfigEditActivity).saveAndExit()
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = LayoutEditConfigBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.config_settings)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.editor.colorScheme = mkTheme()
        binding.editor.language = JsonLanguage()
        binding.editor.onChangeListener = OnChangeListener {
            config = binding.editor.text.toString()
            if (!dirty) {
                dirty = true
                DataStore.dirty = true
            }
        }
        binding.editor.setHorizontallyScrolling(true)
        binding.actionTab.setOnClickListener {
            binding.editor.insert(binding.editor.tab())
        }
        val extendedKeyboard = findViewById<ExtendedKeyboard>(R.id.extended_keyboard)
        extendedKeyboard.setKeyListener { char -> binding.editor.insert(char) }
        extendedKeyboard.setHasFixedSize(true)
        extendedKeyboard.submitList("{}();,.=|&![]<>+-/*?:_".map { it.toString() })
        extendedKeyboard.setBackgroundColor(getColorAttr(R.attr.primaryOrTextPrimary))

        runOnDefaultDispatcher {
            config = DataStore.serverConfig

            onMainDispatcher {
                binding.editor.setTextContent(config)
            }
        }
    }

    fun saveAndExit() {
        config = try {
            JSONObject(config).toStringPretty()
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
                .setMessage(e.readableMessage).show()
            return
        }

        DataStore.serverConfig = config
        finish()
    }

    override fun onBackPressed() {
        if (dirty) UnsavedChangesDialogFragment().apply { key() }
            .show(supportFragmentManager, null) else super.onBackPressed()
    }


    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.profile_apply_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_apply -> {
                saveAndExit()
                return true
            }
        }
        return super.onOptionsItemSelected(item)

    }

    fun mkTheme(): ColorScheme {
        val colorPrimary = getColorAttr(R.attr.colorPrimary)
        val colorPrimaryDark = getColorAttr(R.attr.colorPrimaryDark)

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

}
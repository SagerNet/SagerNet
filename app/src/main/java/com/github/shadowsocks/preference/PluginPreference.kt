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

package com.github.shadowsocks.preference

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.github.shadowsocks.plugin.PluginList
import com.github.shadowsocks.plugin.PluginManager
import io.nekohasekai.sagernet.R

class PluginPreference(context: Context, attrs: AttributeSet? = null) : ListPreference(
    context, attrs
) {
    companion object FallbackProvider : SummaryProvider<PluginPreference> {
        override fun provideSummary(preference: PluginPreference) = preference.selectedEntry?.label
            ?: preference.unknownValueSummary.format(preference.value)
    }

    lateinit var plugins: PluginList
    val selectedEntry get() = plugins.lookup[value]
    private val entryIcon: Drawable? get() = selectedEntry?.icon
    private val unknownValueSummary = context.getString(R.string.plugin_unknown)

    private var listener: OnPreferenceChangeListener? = null
    override fun getOnPreferenceChangeListener(): OnPreferenceChangeListener? = listener
    override fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {
        this.listener = listener
    }

    init {
        super.setOnPreferenceChangeListener { preference, newValue ->
            val listener = listener
            if (listener == null || listener.onPreferenceChange(preference, newValue)) {
                value = newValue.toString()
                icon = entryIcon
                true
            } else false
        }
    }

    fun init(skipInternal: Boolean = false) {
        plugins = PluginManager.fetchPlugins(skipInternal)
        entryValues = plugins.lookup.map { it.key }.toTypedArray()
        icon = entryIcon
        summaryProvider = FallbackProvider
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        init()
    }
}

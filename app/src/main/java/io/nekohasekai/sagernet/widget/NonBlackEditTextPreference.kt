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

package io.nekohasekai.sagernet.widget

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import com.takisoft.preferencex.EditTextPreference
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ui.profile.ConfigEditActivity

class NonBlackEditTextPreference : EditTextPreference {

    var defaultValue: String? = null

    constructor(context: Context) : this(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(context, attrs, com.takisoft.preferencex.R.attr.editTextPreferenceStyle)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.Preference, defStyleAttr, defStyleRes
        )
        if (a.hasValue(androidx.preference.R.styleable.Preference_defaultValue)) {
            defaultValue = onGetDefaultValue(
                a, androidx.preference.R.styleable.Preference_defaultValue
            )?.toString()
        } else if (a.hasValue(androidx.preference.R.styleable.Preference_android_defaultValue)) {
            defaultValue = onGetDefaultValue(
                a, androidx.preference.R.styleable.Preference_android_defaultValue
            )?.toString()
        }
    }

    override fun setOnPreferenceChangeListener(onPreferenceChangeListener: OnPreferenceChangeListener?) {
        super.setOnPreferenceChangeListener { preference, newValue ->
            newValue as String
            if (newValue.isBlank()) {
                text = defaultValue
                false
            } else {
                onPreferenceChangeListener?.onPreferenceChange(preference,newValue) ?: true
            }
        }
    }

    init {
        onPreferenceChangeListener = null
    }

}
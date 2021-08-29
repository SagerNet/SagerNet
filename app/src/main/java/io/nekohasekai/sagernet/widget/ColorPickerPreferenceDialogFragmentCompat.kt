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

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.preference.PreferenceDialogFragmentCompat
import com.takisoft.colorpicker.ColorPickerDialog
import com.takisoft.colorpicker.OnColorSelectedListener

class ColorPickerPreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat(),
    OnColorSelectedListener {
    private var pickedColor = 0
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val pref = colorPickerPreference
        val params = ColorPickerDialog.Params.Builder(context)
            .setSelectedColor(pref.getColor())
            .setColors(pref.colors)
//            .setColorContentDescriptions(pref.colorDescriptions)
            .setSize(pref.size)
            .setSortColors(pref.isSortColors)
            .setColumns(pref.columns)
            .build()
        val dialog = ColorPickerDialog(requireContext(), this, params)
        dialog.setTitle(pref.dialogTitle)
        return dialog
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        val preference = colorPickerPreference
        if (positiveResult && preference.callChangeListener(pickedColor)) {
            preference.setColor(pickedColor)
        }
    }

    override fun onColorSelected(color: Int) {
        pickedColor = color
        super.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
    }

    val colorPickerPreference: ColorPickerPreference
        get() = preference as ColorPickerPreference
}
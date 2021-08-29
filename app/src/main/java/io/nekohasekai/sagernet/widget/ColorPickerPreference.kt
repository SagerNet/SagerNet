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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.DialogPreference
import androidx.preference.PreferenceViewHolder
import com.takisoft.colorpicker.ColorPickerDialog
import com.takisoft.colorpicker.ColorStateDrawable
import com.takisoft.preferencex.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app

class ColorPickerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {
    companion object {
        init {
            PreferenceFragmentCompat.registerPreferenceFragment(
                ColorPickerPreference::class.java,
                ColorPickerPreferenceDialogFragmentCompat::class.java
            )
        }
    }

    init {
        widgetLayoutResource = R.layout.preference_widget_color_swatch
    }

    val colors = app.resources.getIntArray(R.array.material_colors)
    lateinit var colorDescriptions: Array<CharSequence>
    private var colorIndex = 0
    var columns = 0

    @get:ColorPickerDialog.Size
    var size = 0
    var isSortColors = false
    private var colorWidget: ImageView? = null

    @SuppressLint("RestrictedApi")
    constructor(context: Context, attrs: AttributeSet?) : this(
        context, attrs, TypedArrayUtils.getAttr(
            context, R.attr.dialogPreferenceStyle,
            android.R.attr.dialogPreferenceStyle
        )
    )

    constructor(context: Context) : this(context, null)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        colorWidget = holder.findViewById(R.id.color_picker_widget) as ImageView
        setColorOnWidget(colors[colorIndex])
    }

    private fun setColorOnWidget(color: Int) {
        if (colorWidget == null) {
            return
        }
        val colorDrawable = arrayOf(
            ContextCompat.getDrawable(
                context, R.drawable.colorpickerpreference_pref_swatch
            )
        )
        colorWidget!!.setImageDrawable(ColorStateDrawable(colorDrawable, color))
    }

    /**
     * Returns the current color.
     *
     * @return The current color.
     */
    fun getColor(): Int {
        return colorIndex
    }

    fun setColor(colorIndex: Int) {
        setInternalColor(colors.indexOfFirst { it == colorIndex }, false)
    }

    private fun setInternalColor(colorIndexToSet: Int, force: Boolean) {
        val colorIndex = if (colorIndexToSet >= colors.size || colorIndexToSet < 0) 1 else colorIndexToSet
        val oldColor = getPersistedInt(2) - 1
        val changed = oldColor != colorIndex
        if (changed || force) {
            this.colorIndex = colorIndex
            persistInt(colorIndex + 1)
            setColorOnWidget(colors[colorIndex])
            notifyChanged()
        }
    }

    override fun onSetInitialValue(defaultValueObj: Any?) {
        setInternalColor(
            getPersistedInt(
                2
            ) - 1, true
        )
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        if (isPersistent) {
            // No need to save instance state since it's persistent
            return superState
        }
        val myState = SavedState(superState)
        myState.color = colorIndex
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state.javaClass != SavedState::class.java) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state)
            return
        }
        val myState = state as SavedState
        super.onRestoreInstanceState(myState.superState)
        colorIndex = myState.color
    }

    private class SavedState : BaseSavedState {
        var color = 0

        constructor(source: Parcel) : super(source) {
            color = source.readInt()
        }

        constructor(superState: Parcelable?) : super(superState) {}

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(color)
        }

        companion object {
            @JvmField
            val CREATOR: Creator<SavedState> = object : Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

}
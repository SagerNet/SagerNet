package com.takisoft.preferencex;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.takisoft.preferencex.colorpicker.R;

import com.takisoft.colorpicker.ColorPickerDialog;
import com.takisoft.colorpicker.ColorPickerDialog.Size;
import com.takisoft.colorpicker.ColorStateDrawable;

public class ColorPickerPreference extends DialogPreference {

    static {
        PreferenceFragmentCompat.registerPreferenceFragment(ColorPickerPreference.class, ColorPickerPreferenceDialogFragmentCompat.class);
    }

    private int[] colors;
    private CharSequence[] colorDescriptions;
    private int color;
    private int columns;
    private int size;
    private boolean sortColors;

    private ImageView colorWidget;

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerPreference, defStyleAttr, 0);

        int colorsId = a.getResourceId(R.styleable.ColorPickerPreference_pref_colors, R.array.color_picker_default_colors);

        if (colorsId != 0) {
            colors = context.getResources().getIntArray(colorsId);
        }

        colorDescriptions = a.getTextArray(R.styleable.ColorPickerPreference_pref_colorDescriptions);
        color = a.getColor(R.styleable.ColorPickerPreference_pref_currentColor, 0);
        columns = a.getInt(R.styleable.ColorPickerPreference_pref_columns, 0);
        size = a.getInt(R.styleable.ColorPickerPreference_pref_size, ColorPickerDialog.SIZE_SMALL);
        sortColors = a.getBoolean(R.styleable.ColorPickerPreference_pref_sortColors, false);

        a.recycle();

        setWidgetLayoutResource(R.layout.preference_widget_color_swatch);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("RestrictedApi")
    public ColorPickerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.dialogPreferenceStyle,
                android.R.attr.dialogPreferenceStyle));
    }

    public ColorPickerPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        colorWidget = (ImageView) holder.findViewById(R.id.color_picker_widget);
        setColorOnWidget(color);
    }

    private void setColorOnWidget(int color) {
        if (colorWidget == null) {
            return;
        }

        Drawable[] colorDrawable = new Drawable[]
                {ContextCompat.getDrawable(getContext(), R.drawable.colorpickerpreference_pref_swatch)};
        colorWidget.setImageDrawable(new ColorStateDrawable(colorDrawable, color));
    }

    /**
     * Returns the current color.
     *
     * @return The current color.
     */
    public int getColor() {
        return color;
    }

    /**
     * Sets the current color.
     *
     * @param color The current color.
     */
    public void setColor(int color) {
        setInternalColor(color, false);
    }

    /**
     * Returns all of the available pref_colors.
     *
     * @return The available pref_colors.
     */
    public int[] getColors() {
        return colors;
    }

    /**
     * Sets the available pref_colors.
     *
     * @param colors The available pref_colors.
     */
    public void setColors(int[] colors) {
        this.colors = colors;
    }

    /**
     * Returns whether the available pref_colors should be sorted automatically based on their HSV
     * values.
     *
     * @return Whether the available pref_colors should be sorted automatically based on their HSV
     * values.
     */
    public boolean isSortColors() {
        return sortColors;
    }

    /**
     * Sets whether the available pref_colors should be sorted automatically based on their HSV
     * values. The sorting does not modify the order of the original pref_colors supplied via
     * {@link #setColors(int[])} or the XML attribute {@code app:pref_colors}.
     *
     * @param sortColors Whether the available pref_colors should be sorted automatically based on their
     *                   HSV values.
     */
    public void setSortColors(boolean sortColors) {
        this.sortColors = sortColors;
    }

    /**
     * Returns the available pref_colors' descriptions that can be used by accessibility services.
     *
     * @return The available pref_colors' descriptions.
     */
    public CharSequence[] getColorDescriptions() {
        return colorDescriptions;
    }

    /**
     * Sets the available pref_colors' descriptions that can be used by accessibility services.
     *
     * @param colorDescriptions The available pref_colors' descriptions.
     */
    public void setColorDescriptions(CharSequence[] colorDescriptions) {
        this.colorDescriptions = colorDescriptions;
    }

    /**
     * Returns the number of pref_columns to be used in the picker dialog for displaying the available
     * pref_colors. If the value is less than or equals to 0, the number of pref_columns will be determined
     * automatically by the system using FlexboxLayoutManager.
     *
     * @return The number of pref_columns to be used in the picker dialog.
     * @see com.google.android.flexbox.FlexboxLayoutManager
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Sets the number of pref_columns to be used in the picker dialog for displaying the available
     * pref_colors. If the value is less than or equals to 0, the number of pref_columns will be determined
     * automatically by the system using FlexboxLayoutManager.
     *
     * @param columns The number of pref_columns to be used in the picker dialog. Use 0 to set it to
     *                'auto' mode.
     * @see com.google.android.flexbox.FlexboxLayoutManager
     */
    public void setColumns(int columns) {
        this.columns = columns;
    }

    /**
     * Returns the size of the color swatches in the dialog. It can be either
     * {@link ColorPickerDialog#SIZE_SMALL} or {@link ColorPickerDialog#SIZE_LARGE}.
     *
     * @return The size of the color swatches in the dialog.
     * @see ColorPickerDialog#SIZE_SMALL
     * @see ColorPickerDialog#SIZE_LARGE
     */
    @Size
    public int getSize() {
        return size;
    }

    /**
     * Sets the size of the color swatches in the dialog. It can be either
     * {@link ColorPickerDialog#SIZE_SMALL} or {@link ColorPickerDialog#SIZE_LARGE}.
     *
     * @param size The size of the color swatches in the dialog. It can be either
     *             {@link ColorPickerDialog#SIZE_SMALL} or {@link ColorPickerDialog#SIZE_LARGE}.
     * @see ColorPickerDialog#SIZE_SMALL
     * @see ColorPickerDialog#SIZE_LARGE
     */
    public void setSize(@Size int size) {
        this.size = size;
    }

    private void setInternalColor(int color, boolean force) {
        int oldColor = getPersistedInt(0);

        boolean changed = oldColor != color;

        if (changed || force) {
            this.color = color;

            persistInt(color);

            setColorOnWidget(color);

            notifyChanged();
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValueObj) {
        final String defaultValue = (String) defaultValueObj;
        setInternalColor(getPersistedInt(!TextUtils.isEmpty(defaultValue) ? Color.parseColor(defaultValue) : 0), true);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.color = getColor();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setColor(myState.color);
    }

    private static class SavedState extends BaseSavedState {
        private int color;

        public SavedState(Parcel source) {
            super(source);
            color = source.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(color);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}

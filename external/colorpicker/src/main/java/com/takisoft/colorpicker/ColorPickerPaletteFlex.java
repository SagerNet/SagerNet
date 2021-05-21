/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.takisoft.colorpicker;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;

/**
 * A color picker custom view which creates an grid of color squares.  The number of squares per
 * row (and the padding between the squares) is determined by the user.
 */
public class ColorPickerPaletteFlex extends RecyclerView implements OnColorSelectedListener {
    public OnColorSelectedListener mOnColorSelectedListener;

    private String mDescription;
    private String mDescriptionSelected;

    public ColorPickerPaletteFlex(Context context) {
        this(context, null);
    }

    public ColorPickerPaletteFlex(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerPaletteFlex(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(context, FlexDirection.ROW, FlexWrap.WRAP);
        setLayoutManager(layoutManager);

        Resources res = getResources();

        mDescription = res.getString(R.string.color_swatch_description);
        mDescriptionSelected = res.getString(R.string.color_swatch_description_selected);

        ColorPickerDialog.Params.Builder paramsBuilder = new ColorPickerDialog.Params.Builder(context);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerPaletteFlex, defStyle, 0);
        int colorsResId = a.getResourceId(R.styleable.ColorPickerPaletteFlex_cpl_colors, R.array.color_picker_default_colors);

        if (colorsResId > 0) {
            int[] colors = context.getResources().getIntArray(colorsResId);
            paramsBuilder.setColors(colors);
        }

        paramsBuilder
                .setSelectedColor(a.getInt(R.styleable.ColorPickerPaletteFlex_cpl_currentColor, 0))
                .setSortColors(a.getBoolean(R.styleable.ColorPickerPaletteFlex_cpl_sortColors, false))
                .setColumns(a.getInt(R.styleable.ColorPickerPaletteFlex_cpl_columns, 0))
                .setSize(a.getInt(R.styleable.ColorPickerPaletteFlex_cpl_size, ColorPickerDialog.SIZE_SMALL))
                .setColorContentDescriptions(a.getTextArray(R.styleable.ColorPickerPaletteFlex_cpl_colorDescriptions));

        a.recycle();

        setup(paramsBuilder.build());
    }

    public void setup(@NonNull ColorPickerDialog.Params params) {
        if (params.mColors == null) {
            throw new IllegalArgumentException("The supplied params (" + params + ") does not " +
                    "contain colors.");
        }

        setAdapter(new ColorAdapter(params, this, mDescription, mDescriptionSelected));

        final int n = params.mColors.length;
        for (int i = 0; i < n; i++) {
            if (params.mColors[i] == params.mSelectedColor) {
                scrollToPosition(i);
                break;
            }
        }
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.mOnColorSelectedListener = listener;
    }

    @Override
    public void onColorSelected(int color) {
        if (mOnColorSelectedListener != null) {
            mOnColorSelectedListener.onColorSelected(color);
        }
    }

    private static class ColorAdapter extends RecyclerView.Adapter<ColorHolder> {
        private ColorPickerDialog.Params params;
        private OnColorSelectedListener colorSelectedListener;

        private final String description;
        private final String descriptionSelected;

        private ColorAdapter(ColorPickerDialog.Params params, OnColorSelectedListener colorSelectedListener, String description, String descriptionSelected) {
            this.params = params;
            this.colorSelectedListener = colorSelectedListener;
            this.description = description;
            this.descriptionSelected = descriptionSelected;
        }

        @Override
        public ColorHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ColorPickerSwatch view = new ColorPickerSwatch(parent.getContext());
            view.setOnColorSelectedListener(colorSelectedListener);

            FlexboxLayoutManager.LayoutParams layoutParams = new FlexboxLayoutManager.LayoutParams(params.mSwatchLength, params.mSwatchLength);
            layoutParams.setMargins(params.mMarginSize, params.mMarginSize, params.mMarginSize, params.mMarginSize);

            layoutParams.setFlexGrow(0);
            layoutParams.setFlexShrink(0);
            view.setLayoutParams(layoutParams);

            return new ColorHolder(view);
        }

        @Override
        public void onBindViewHolder(ColorHolder holder, int position) {
            ColorPickerSwatch swatch = (ColorPickerSwatch) holder.itemView;

            boolean selected = params.mSelectedColor == params.mColors[position];

            swatch.setColor(params.mColors[position]);
            swatch.setChecked(selected);

            FlexboxLayoutManager.LayoutParams layoutParams = (FlexboxLayoutManager.LayoutParams) swatch.getLayoutParams();

            if (params.mColumns > 0 && position % params.mColumns == 0) {
                layoutParams.setWrapBefore(true);
            } else {
                layoutParams.setWrapBefore(false);
            }

            setSwatchDescription(position, selected, swatch, params.mColorContentDescriptions);
        }

        private void setSwatchDescription(int index, boolean selected, View swatch, CharSequence[] contentDescriptions) {
            CharSequence description;
            if (contentDescriptions != null && contentDescriptions.length > index) {
                description = contentDescriptions[index];
            } else {
                int accessibilityIndex = index + 1;

                if (selected) {
                    description = String.format(descriptionSelected, accessibilityIndex);
                } else {
                    description = String.format(this.description, accessibilityIndex);
                }
            }
            swatch.setContentDescription(description);
        }

        @Override
        public int getItemCount() {
            return params.mColors.length;
        }
    }

    private static class ColorHolder extends RecyclerView.ViewHolder {

        ColorHolder(View itemView) {
            super(itemView);
        }
    }
}

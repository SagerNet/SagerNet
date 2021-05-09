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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import androidx.appcompat.content.res.AppCompatResources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Creates a circular swatch of a specified color.  Adds a checkmark if marked as checked.
 */
public class ColorPickerSwatch extends FrameLayout implements View.OnClickListener {
    private int mColor;
    private ImageView mSwatchImage;
    private ImageView mCheckmarkImage;
    private OnColorSelectedListener mOnColorSelectedListener;

    public ColorPickerSwatch(Context context) {
        super(context);

        LayoutInflater.from(context).inflate(R.layout.color_picker_swatch, this);
        mSwatchImage = findViewById(R.id.color_picker_swatch);
        mCheckmarkImage = findViewById(R.id.color_picker_checkmark);
        mCheckmarkImage.setImageDrawable(getCheckmark(context));

        setOnClickListener(this);
    }

    private Drawable getCheckmark(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return AppCompatResources.getDrawable(context, R.drawable.color_picker_checkmark);
        } else {
            Drawable check = AppCompatResources.getDrawable(context, R.drawable.color_picker_check_tick);
            Drawable base = AppCompatResources.getDrawable(context, R.drawable.color_picker_check_base);

            int basePadding = context.getResources().getDimensionPixelSize(R.dimen.color_picker_checkmark_base_padding);
            int tickPadding = context.getResources().getDimensionPixelSize(R.dimen.color_picker_checkmark_tick_padding);
            LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{base, check});
            layerDrawable.setLayerInset(0, basePadding, basePadding, basePadding, basePadding);
            layerDrawable.setLayerInset(1, tickPadding, tickPadding, tickPadding, tickPadding);

            return layerDrawable;
        }
    }

    protected void setColor(int color) {
        Drawable[] colorDrawable = new Drawable[]
                {getContext().getResources().getDrawable(R.drawable.color_picker_swatch)};
        mSwatchImage.setImageDrawable(new ColorStateDrawable(colorDrawable, color));
        mColor = color;
    }

    public void setOnColorSelectedListener(OnColorSelectedListener colorSelectedListener) {
        this.mOnColorSelectedListener = colorSelectedListener;
    }

    public void setChecked(boolean checked) {
        if (checked) {
            mCheckmarkImage.setVisibility(View.VISIBLE);
        } else {
            mCheckmarkImage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        if (mOnColorSelectedListener != null) {
            mOnColorSelectedListener.onColorSelected(mColor);
        }
    }
}

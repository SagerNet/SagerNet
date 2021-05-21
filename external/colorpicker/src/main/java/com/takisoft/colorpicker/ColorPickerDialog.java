package com.takisoft.colorpicker;

import android.content.Context;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

public class ColorPickerDialog extends AlertDialog implements OnColorSelectedListener {
    public static final int SIZE_LARGE = 1;
    public static final int SIZE_SMALL = 2;

    @IntDef({SIZE_LARGE, SIZE_SMALL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Size {
    }

    private final ColorPickerPaletteFlex mPalette;
    private final ProgressBar mProgress;

    private OnColorSelectedListener listener;

    private Params params;


    public ColorPickerDialog(@NonNull Context context, OnColorSelectedListener listener, Params params) {
        this(context, 0, listener, params);
    }

    public ColorPickerDialog(@NonNull Context context, int themeResId, OnColorSelectedListener listener, Params params) {
        super(context, resolveDialogTheme(context, themeResId));

        final Context themeContext = getContext();

        this.listener = listener;
        this.params = params;

        View view = LayoutInflater.from(themeContext).inflate(R.layout.color_picker_dialog, this.getListView());
        setView(view);

        mProgress = view.findViewById(android.R.id.progress);
        mPalette = view.findViewById(R.id.color_picker);
        //mPalette.init(params.mSize, params.mColumns, this);
        mPalette.setOnColorSelectedListener(this);

        if (params.mColumns > 0) {
            mPalette.getLayoutParams().width = mPalette.getPaddingLeft() + mPalette.getPaddingRight() + params.mColumns * (params.mSwatchLength + 2 * params.mMarginSize);
        }

        if (params.mColors != null) {
            showPaletteView();
        }

        /*
        setButton(BUTTON_POSITIVE, themeContext.getString(android.R.string.ok), this);
        setButton(BUTTON_NEGATIVE, themeContext.getString(android.R.string.cancel), this);
         */
    }

    public void showPaletteView() {
        if (mProgress != null && mPalette != null) {
            mProgress.setVisibility(View.GONE);
            refreshPalette();
            mPalette.setVisibility(View.VISIBLE);
        }
    }

    private void refreshPalette() {
        if (mPalette != null && params.mColors != null) {
            mPalette.setup(params);
            //mPalette.drawPalette(params.mColors, params.mSelectedColor, params.mColorContentDescriptions);
        }
    }

    @Override
    public void onColorSelected(int color) {
        if (listener != null) {
            listener.onColorSelected(color);
        }

        if (color != params.mSelectedColor) {
            params.mSelectedColor = color;
            // Redraw palette to show checkmark on newly selected color before dismissing.
            //mPalette.drawPalette(params.mColors, params.mSelectedColor);
            mPalette.setup(params);
        }

        dismiss();
    }

    private static int resolveDialogTheme(Context context, int resId) {
        if (resId == 0) {
            return R.style.ThemeOverlay_Material_Dialog_ColorPicker;
        } else {
            return resId;
        }
    }

    public static class Params implements Parcelable {
        int[] mColors;
        CharSequence[] mColorContentDescriptions;
        int mSelectedColor;
        int mColumns;
        int mSize;

        int mSwatchLength;
        int mMarginSize;

        int mSelectedColorIndex = -1;

        private Params() {
        }

        public static class Builder {
            private int[] colors;
            private CharSequence[] colorContentDescriptions;
            private int selectedColor;
            private int columns;

            private boolean sortColors = false;

            @Size
            private int size = SIZE_SMALL;

            private Context context;

            public Builder(Context context) {
                this.context = context;
            }

            public Builder setColors(int[] colors) {
                this.colors = colors;
                return this;
            }

            public Builder setColorContentDescriptions(CharSequence[] colorContentDescriptions) {
                this.colorContentDescriptions = colorContentDescriptions;
                return this;
            }

            public Builder setSelectedColor(int selectedColor) {
                this.selectedColor = selectedColor;
                return this;
            }

            /**
             * Sets the number of columns to be used in the dialog. If the set value is less than or
             * equals to zero, the column number will be calculated automatically.
             *
             * @param columns the number of columns
             * @return the Builder instance
             */
            public Builder setColumns(int columns) {
                this.columns = columns;
                return this;
            }

            public Builder setSortColors(boolean sortColors) {
                this.sortColors = sortColors;
                return this;
            }

            public Builder setSize(@Size int size) {
                this.size = size;
                return this;
            }

            public Params build() {
                Resources res = context.getResources();

                if (colors == null) {
                    colors = res.getIntArray(R.array.color_picker_default_colors);
                }

                Params params = new Params();

                if (sortColors) {
                    final int n = colors.length;

                    Integer[] colorObjs = new Integer[n];
                    for (int i = 0; i < n; i++) {
                        colorObjs[i] = colors[i];
                    }

                    Arrays.sort(colorObjs, new HsvColorComparator());

                    int[] sortedColors = new int[n];

                    for (int i = 0; i < n; i++) {
                        sortedColors[i] = colorObjs[i];
                    }

                    params.mColors = sortedColors;
                } else {
                    params.mColors = colors;
                }
                params.mColorContentDescriptions = colorContentDescriptions;
                params.mSelectedColor = selectedColor;
                params.mColumns = columns;
                params.mSize = size;

                if (size == ColorPickerDialog.SIZE_LARGE) {
                    params.mSwatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_large);
                    params.mMarginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_large);
                } else {
                    params.mSwatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_small);
                    params.mMarginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_small);
                }

                return params;
            }
        }

        protected Params(Parcel in) {
            mColors = in.createIntArray();
            mSelectedColor = in.readInt();
            mColumns = in.readInt();
            mSize = in.readInt();
            mSwatchLength = in.readInt();
            mMarginSize = in.readInt();
            mSelectedColorIndex = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeIntArray(mColors);
            dest.writeInt(mSelectedColor);
            dest.writeInt(mColumns);
            dest.writeInt(mSize);
            dest.writeInt(mSwatchLength);
            dest.writeInt(mMarginSize);
            dest.writeInt(mSelectedColorIndex);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Params> CREATOR = new Creator<Params>() {
            @Override
            public Params createFromParcel(Parcel in) {
                return new Params(in);
            }

            @Override
            public Params[] newArray(int size) {
                return new Params[size];
            }
        };
    }
}

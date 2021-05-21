package com.takisoft.preferencex;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceViewHolder;

public class SwitchPreferenceCompat extends androidx.preference.SwitchPreferenceCompat {
    private static final int[] ATTRS = new int[]{androidx.appcompat.R.attr.controlBackground, R.attr.colorControlNormal};

    private final View.OnClickListener contentClickListener = new View.OnClickListener() {
        @SuppressLint("RestrictedApi")
        @Override
        public void onClick(View v) {
            performClick((View) v.getParent());
        }
    };

    private final View.OnClickListener widgetClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final boolean newValue = !isChecked();

            if (callChangeListener(newValue)) {
                setChecked(newValue);
            }
        }
    };

    private boolean withSeparator = false;

    {
        refreshWithSeparator(false);
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchPreferenceCompat(Context context) {
        super(context);
    }

    void refresh() {
        if (!withSeparator) {
            return;
        }

        boolean value = getPersistedBoolean(false);
        boolean oldPersistent = isPersistent();

        setPersistent(false);
        setChecked(value);
        setPersistent(oldPersistent);
        /*boolean old = mChecked;
        mChecked = getPersistedBoolean(false);

        if (old != mChecked) {
            notifyDependencyChange(shouldDisableDependents());
            notifyChanged();
        }*/
    }

    @Override
    protected void onClick() {
        if (!withSeparator) {
            super.onClick();
        }
    }

    private void refreshWithSeparator(boolean changeLayout) {
        if (setWithSeparator(getFragment() != null) && changeLayout) {
            notifyHierarchyChanged();
        }
    }

    private boolean setWithSeparator(boolean withSeparator) {
        if (this.withSeparator == withSeparator) {
            return false;
        }

        this.withSeparator = withSeparator;
        if (withSeparator) {
            setLayoutResource(R.layout.preference_material_ext);
        } else {
            setLayoutResource(androidx.preference.R.layout.preference_material);
        }

        return true;
    }

    @Override
    public void setFragment(String fragment) {
        super.setFragment(fragment);
        refreshWithSeparator(true);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (withSeparator) {
            holder.findViewById(android.R.id.widget_frame).setOnClickListener(widgetClickListener);
            holder.findViewById(R.id.pref_content_frame).setOnClickListener(contentClickListener);
            //?attr/controlBackground
            //androidx.appcompat.R.attr.controlBackground

            final TypedArray typedArray = getContext().obtainStyledAttributes(ATTRS);

            if (typedArray.length() > 0 && typedArray.getIndexCount() > 0) {
                int id = typedArray.getResourceId(0, 0);
                if (id != 0) {
                    Drawable bgDrawable = AppCompatResources.getDrawable(getContext(), id);
                    holder.findViewById(androidx.preference.R.id.switchWidget).setBackgroundDrawable(bgDrawable);
                }

                ColorStateList separatorColor = typedArray.getColorStateList(1);
                if (separatorColor != null) {
                    int[] stateSet = isEnabled() ? new int[]{android.R.attr.state_enabled} : new int[]{-android.R.attr.state_enabled};
                    int dividerColor = separatorColor.getColorForState(stateSet, separatorColor.getDefaultColor());
                    holder.findViewById(R.id.pref_separator).setBackgroundColor(dividerColor);
                }
            }

            typedArray.recycle();
        }

        holder.itemView.setClickable(!withSeparator);
        holder.itemView.setFocusable(!withSeparator);
    }
}

package com.takisoft.preferencex;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

/**
 * An extended {@link androidx.preference.PreferenceCategory} that allows the user to set the color
 * and the visibility of the title.
 */
public class PreferenceCategory extends androidx.preference.PreferenceCategory {
    private static final int[] CATEGORY_ATTRS = new int[]{R.attr.colorAccent};

    protected int color;
    protected View itemView;

    public PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PreferenceCategory, defStyleAttr, 0);
        color = a.getColor(R.styleable.PreferenceCategory_pref_categoryColor, 0);
        a.recycle();
    }

    public PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PreferenceCategory(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.preferenceCategoryStyle, android.R.attr.preferenceCategoryStyle));
    }

    public PreferenceCategory(Context context) {
        this(context, null);
    }

    private void setTitleVisibility(View itemView, boolean isVisible) {
        if (itemView == null) {
            return;
        }

        final RecyclerView.LayoutParams currentParams = (RecyclerView.LayoutParams) itemView.getLayoutParams();
        final RecyclerView.LayoutParams param;

        final boolean wasHidden = itemView.getTag() != null && currentParams.width == 0;

        if (itemView.getTag() == null) {
            param = new RecyclerView.LayoutParams((ViewGroup.MarginLayoutParams) currentParams);
            itemView.setTag(param);
        } else {
            param = (RecyclerView.LayoutParams) itemView.getTag();
        }

        if (isVisible) {
            if (itemView.getVisibility() == View.GONE || wasHidden) {
                currentParams.width = param.width;
                currentParams.height = param.height;
                currentParams.leftMargin = param.leftMargin;
                currentParams.rightMargin = param.rightMargin;
                currentParams.topMargin = param.topMargin;
                currentParams.bottomMargin = param.bottomMargin;
                itemView.setVisibility(View.VISIBLE);
            }
        } else {
            if (itemView.getVisibility() == View.VISIBLE || !wasHidden) {
                currentParams.width = 0;
                currentParams.height = 0;
                currentParams.leftMargin = 0;
                currentParams.rightMargin = 0;
                currentParams.topMargin = 0;
                currentParams.bottomMargin = 0;
                itemView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);

        setTitleVisibility(itemView, !TextUtils.isEmpty(getTitle()));
    }

    public void setColor(@ColorInt int color) {
        this.color = color;
    }

    public void setColorResource(@ColorRes int resId) {
        int color;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            color = getContext().getResources().getColor(resId);
        } else {
            color = getContext().getColor(resId);
        }

        setColor(color);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        itemView = holder.itemView;

        TextView titleView = (TextView) holder.findViewById(android.R.id.title);

        if (titleView != null) {
            final TypedArray typedArray = getContext().obtainStyledAttributes(CATEGORY_ATTRS);

            if (typedArray.length() > 0 && typedArray.getIndexCount() > 0) {
                final int accentColor = typedArray.getColor(typedArray.getIndex(0), 0xff4081); // defaults to pink
                titleView.setTextColor(color == 0 ? accentColor : color);
            }

            typedArray.recycle();
        }

        boolean isVisible = !TextUtils.isEmpty(getTitle());
        setTitleVisibility(holder.itemView, isVisible);
        /*if (!isVisible) {
            return;
        }*/
    }
}

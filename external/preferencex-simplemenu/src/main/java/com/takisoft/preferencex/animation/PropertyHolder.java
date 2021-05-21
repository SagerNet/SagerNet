package com.takisoft.preferencex.animation;

import android.view.View;

import com.takisoft.preferencex.drawable.FixedBoundsDrawable;

/**
 * Holder class holds background drawable and content view.
 */

public class PropertyHolder {

    private final FixedBoundsDrawable mBackground;
    private final View mContentView;

    public PropertyHolder(FixedBoundsDrawable background, View contentView) {
        mBackground = background;
        mContentView = contentView;
    }

    public FixedBoundsDrawable getBackground() {
        return mBackground;
    }

    public View getContentView() {
        return mContentView;
    }
}

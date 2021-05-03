package com.takisoft.preferencex.drawable;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A wrapped {@link Drawable} that force use its own bounds to draw.
 *
 * It maybe a little dirty. But if we don't do that, during the expanding animation, there will be
 * one or two frame using wrong bounds because of parent view sets bounds.
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FixedBoundsDrawable extends Drawable implements Drawable.Callback {

    private final Drawable mDrawable;
    private final Rect mFixedBounds = new Rect();

    public FixedBoundsDrawable(Drawable wrappedDrawable) {
        mDrawable = wrappedDrawable;
    }

    public Rect getFixedBounds() {
        return mFixedBounds;
    }

    public void setFixedBounds(@NonNull Rect bounds) {
        setFixedBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    public void setFixedBounds(int left, int top, int right, int bottom) {
        mFixedBounds.set(left, top, right, bottom);
        setBounds(left, top, right, bottom);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        mDrawable.getOutline(outline);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        mDrawable.setBounds(mFixedBounds);
        mDrawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        mDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mDrawable.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return mDrawable.getOpacity();
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.invalidateDrawable(this);
        }
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.scheduleDrawable(this, what, when);
        }
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.unscheduleDrawable(this, what);
        }
    }
}

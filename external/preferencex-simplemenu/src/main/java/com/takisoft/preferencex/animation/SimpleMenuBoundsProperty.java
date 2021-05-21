package com.takisoft.preferencex.animation;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.util.Property;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SimpleMenuBoundsProperty extends Property<PropertyHolder, Rect> {

    public static final Property<PropertyHolder, Rect> BOUNDS;

    static {
        BOUNDS = new SimpleMenuBoundsProperty("bounds");
    }

    @Override
    public Rect get(PropertyHolder holder) {
        return holder.getBackground().getFixedBounds();
    }

    @Override
    public void set(PropertyHolder holder, Rect value) {
        holder.getBackground().setFixedBounds(value);
        holder.getContentView().setClipBounds(value);
    }

    public SimpleMenuBoundsProperty(String name) {
        super(Rect.class, name);
    }
}

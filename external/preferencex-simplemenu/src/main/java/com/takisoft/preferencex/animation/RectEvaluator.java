package com.takisoft.preferencex.animation;

import android.animation.TypeEvaluator;
import android.annotation.SuppressLint;
import android.graphics.Rect;

/**
 * This evaluator can be used to perform type interpolation between {@link Rect}.
 */

public class RectEvaluator implements TypeEvaluator<Rect> {

    private final Rect mMax;
    private final Rect mTemp = new Rect();

    public RectEvaluator(Rect max) {
        mMax = max;
    }

    @SuppressLint("CheckResult")
    @Override
    public Rect evaluate(float fraction, Rect startValue, Rect endValue) {
        mTemp.left    = startValue.left     + (int) ((endValue.left     - startValue.left)    * fraction);
        mTemp.top     = startValue.top      + (int) ((endValue.top      - startValue.top)     * fraction);
        mTemp.right   = startValue.right    + (int) ((endValue.right    - startValue.right)   * fraction);
        mTemp.bottom  = startValue.bottom   + (int) ((endValue.bottom   - startValue.bottom)  * fraction);
        mTemp.setIntersect(mMax, mTemp);
        return mTemp;
    }
}

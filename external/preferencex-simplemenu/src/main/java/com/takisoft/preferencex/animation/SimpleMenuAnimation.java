package com.takisoft.preferencex.animation;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.takisoft.preferencex.drawable.FixedBoundsDrawable;
import com.takisoft.preferencex.widget.SimpleMenuPopupWindow;

/**
 * Helper class to create and start animation of Simple Menu.
 *
 * TODO let params styleable
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SimpleMenuAnimation {

    public static void postStartEnterAnimation(final SimpleMenuPopupWindow popupWindow, final FixedBoundsDrawable background,
                                               final int width, final int height,
                                               final int startX, final int startY, final Rect start,
                                               final int itemHeight, final int elevation, final int selectedIndex) {
        popupWindow.getBackground().setFixedBounds(new Rect());
        popupWindow.getContentView().setClipBounds(new Rect());

        popupWindow.getContentView().post(new Runnable() {
            @Override
            public void run() {
                // return if already dismissed
                if (popupWindow.getContentView().getParent() == null) {
                    return;
                }
                startEnterAnimation(popupWindow.getContentView(), background, width, height, startX, startY, start, itemHeight, elevation, selectedIndex);
            }
        });
    }

    public static void startEnterAnimation(final View view, final FixedBoundsDrawable background,
                                           int width, int height,
                                           int centerX, int centerY, Rect start,
                                           int itemHeight, int elevation, int selectedIndex) {
        PropertyHolder holder = new PropertyHolder(background, view);
        Animator backgroundAnimator = createBoundsAnimator(
                holder, width, height, centerX, centerY, start);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                backgroundAnimator,
                createElevationAnimator((View) view.getParent(), elevation));
        animatorSet.setDuration(backgroundAnimator.getDuration());
        animatorSet.start();

        long delay = 0;

        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                int offset = selectedIndex - i;
                startChild(((ViewGroup) view).getChildAt(i), delay + 30 * Math.abs(offset),
                        offset == 0 ? 0 : (int) (itemHeight * 0.2) * (offset < 0 ? -1 : 1));
            }
        }
    }

    private static void startChild(View child, long delay, int translationY) {
        child.setAlpha(0);

        Animator alphaAnimator = ObjectAnimator.ofFloat(child, "alpha", 0.0f, 1.0f);
        alphaAnimator.setDuration(200);
        alphaAnimator.setInterpolator(new AccelerateInterpolator());

        Animator translationAnimator = ObjectAnimator.ofFloat(child, "translationY", translationY, 0);
        translationAnimator.setDuration(275);
        translationAnimator.setInterpolator(new DecelerateInterpolator());

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(alphaAnimator, translationAnimator);
        animatorSet.setStartDelay(delay);
        animatorSet.start();
    }

    private static Rect[] getBounds(
            int width, int height, int centerX, int centerY) {
        int endWidth = Math.max(centerX, width - centerX);
        int endHeight = Math.max(centerY, height - centerY);

        int endLeft = centerX - endWidth;
        int endRight = centerX + endWidth;
        int endTop = centerY - endHeight;
        int endBottom = centerY + endHeight;

        Rect end = new Rect(endLeft, endTop, endRight, endBottom);
        Rect max = new Rect(0, 0, width, height);

        return new Rect[]{end, max};
    }

    private static Animator createBoundsAnimator(PropertyHolder holder,
                                                 int width, int height, int centerX, int centerY, Rect start) {
        int speed = 4096;

        int endWidth = Math.max(centerX, width - centerX);
        int endHeight = Math.max(centerY, height - centerY);

        Rect[] rect = getBounds(width, height, centerX, centerY);
        Rect end = rect[0];
        Rect max = rect[1];

        long duration = (long) ((float) Math.max(endWidth, endHeight) / speed * 1000);
        duration = Math.max(duration, 150);
        duration = Math.min(duration, 300);

        Animator animator = ObjectAnimator
                .ofObject(holder, SimpleMenuBoundsProperty.BOUNDS, new RectEvaluator(max), start, end);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.setDuration(duration);
        return animator;
    }

    @SuppressWarnings("unchecked")
    private static Animator createElevationAnimator(View view, float elevation) {
        Animator animator = ObjectAnimator.ofObject(view, View.TRANSLATION_Z, (TypeEvaluator) new FloatEvaluator(), -elevation, 0f);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        return animator;
    }
}

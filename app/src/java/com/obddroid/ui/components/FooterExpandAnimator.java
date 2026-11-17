package com.obddroid.ui.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

/**
 * The slide animations of {@link VehicleInfoFooter}'s expanded panel and the dimming overlay behind it:
 * open with a small overshoot, close with a deceleration, the overlay fading in step.
 */
final class FooterExpandAnimator {

    private FooterExpandAnimator() {
    }

    /**
     * Fades the overlay in and grows the panel from zero to its measured height.
     * @param panel the expanded container; its layout params are LinearLayout params
     * @param overlay the dimming view behind the footer, or null
     * @param width the footer width the panel is measured against
     */
    static void expand(View panel, View overlay, int width) {
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(300).start();
        }

        panel.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        final int targetHeight = panel.getMeasuredHeight();

        setHeight(panel, 0);
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(1f);

        ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
        animator.setDuration(500);
        animator.setInterpolator(new OvershootInterpolator(1.2f));
        animator.addUpdateListener(animation -> setHeight(panel, (int) animation.getAnimatedValue()));
        animator.start();
    }

    /**
     * Fades the overlay out and shrinks the panel to zero, then hides it and gives it back WRAP_CONTENT.
     */
    static void collapse(View panel, View overlay) {
        if (overlay != null) {
            overlay.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> overlay.setVisibility(View.GONE)).start();
        }

        ValueAnimator animator = ValueAnimator.ofInt(panel.getHeight(), 0);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(animation -> setHeight(panel, (int) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                panel.setVisibility(View.GONE);
                setHeight(panel, LinearLayout.LayoutParams.WRAP_CONTENT);
            }
        });
        animator.start();
    }

    private static void setHeight(View panel, int height) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) panel.getLayoutParams();
        params.height = height;
        panel.setLayoutParams(params);
    }
}

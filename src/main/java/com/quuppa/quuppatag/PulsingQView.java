/**
 * Quuppa Android Tag Emulation Demo application.
 *
 * Copyright 2025 Quuppa Oy
 *
 * Disclaimer
 * THE SOURCE CODE, DOCUMENTATION AND SPECIFICATIONS ARE PROVIDED “AS IS”. ALL LIABILITIES, WARRANTIES AND CONDITIONS, EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION TO THOSE CONCERNING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT
 * OF THIRD PARTY INTELLECTUAL PROPERTY RIGHTS ARE HEREBY EXCLUDED.
 */
package com.quuppa.quuppatag;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.animation.ArgbEvaluator;

/**
 * Custom view to render Quuppa Q character on it with a pulsing animation.
 */
public class PulsingQView extends androidx.appcompat.widget.AppCompatImageView {
    private boolean pulsing = false;
    private ObjectAnimator animator;

    public PulsingQView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setContentDescription("Quuppa Tag Service Status");

        // Animating alpha on VectorDrawable simply doesn't seem to work, have to do with tint
//        float[] values = new float[18];
//        int index = 0;
//        for (float f = 0.95f; f >= 0.10f; f -= 0.05f) values[index++] = f;

        int steps = 15; // till ~30%
        int[] tintColors = new int[steps + 1];
        for (int i = 0; i <= steps; i++) {
            int gray = 255 - (i * 13);
            tintColors[i] = (255 << 24) | (gray << 16) | (gray << 8) | gray;
        }

        animator = ObjectAnimator.ofInt(getDrawable(), "tint", tintColors);
        // Must use this evaluator, otherwise setTint interpolates to the existing color
        animator.setEvaluator(new ArgbEvaluator());
        animator.setDuration(1000);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
    }

    public void setIsPulsing(boolean isPulsing) {
        this.pulsing = isPulsing;
        if (pulsing) animator.start();
        else animator.cancel();
        getDrawable().setTint(0xffffffff);
        this.invalidate();
        setContentDescription("Quuppa Tag Service " + (pulsing ? "enabled" : "disabled"));
    }
}

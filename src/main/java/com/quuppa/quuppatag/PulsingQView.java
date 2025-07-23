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

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class PulsingQView extends AppCompatImageView {
    private boolean pulsing = false;
    private AnimationDrawable waveAnimation;

    public PulsingQView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setContentDescription("Quuppa Tag Service Status");
        setImageResource(R.drawable.q_white);
    }

    public void setIsPulsing(boolean isPulsing) {
        this.pulsing = isPulsing;
        if (pulsing) {
            setImageResource(R.drawable.wave_animation);
            waveAnimation = (AnimationDrawable) getDrawable();
            waveAnimation.start();
        } else {
            if (waveAnimation != null) {
                waveAnimation.stop();
            }
            setImageResource(R.drawable.q_white);
        }
        setContentDescription("Quuppa Tag Service " + (pulsing ? "enabled" : "disabled"));
    }
}

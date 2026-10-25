package com.sulthan.cardash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** An analog speedometer: ticks and numbers around a 270° dial, a needle and a digital readout. */
public class SpeedoView extends View {
    private static final float START = 135f;
    private static final float SWEEP = 270f;

    private float max = 240, step = 20;
    private String unit = "km/h";
    private float target = Float.NaN;
    private float shown = Float.NaN;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final int textColor, dimColor;

    public SpeedoView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        int accent = GaugeView.color(ctx, R.color.accent);
        textColor = GaugeView.color(ctx, R.color.text);
        dimColor = GaugeView.color(ctx, R.color.text_dim);

        track.setStyle(Paint.Style.STROKE);
        track.setColor(GaugeView.color(ctx, R.color.track));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(accent);
        tick.setStyle(Paint.Style.STROKE);
        num.setColor(dimColor);
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        needle.setStyle(Paint.Style.STROKE);
        needle.setStrokeCap(Paint.Cap.ROUND);
        needle.setColor(accent);
        hub.setStyle(Paint.Style.FILL);
        hub.setColor(GaugeView.color(ctx, R.color.bg));
        hubRing.setStyle(Paint.Style.STROKE);
        hubRing.setColor(accent);
        value.setColor(textColor);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        label.setColor(dimColor);
        label.setTextAlign(Paint.Align.CENTER);
    }

    /** max and step are in the displayed unit, e.g. 240/20 for km/h or 160/20 for mph. */
    public void setRange(float max, float step, String unit) {
        this.max = max;
        this.step = step;
        this.unit = unit;
        invalidate();
    }

    /** NaN shows "--" and rests the needle at zero. */
    public void setValue(double v) {
        target = (float) v;
        if (Float.isNaN(shown) || Float.isNaN(target)) shown = target;
        invalidate();
    }

    private static double rad(float deg) { return Math.toRadians(deg); }

    @Override
    protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight()) - 16;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = size / 2f;
        float stroke = Math.max(2f, size * 0.012f);
        oval.set(cx - r, cy - r, cx + r, cy + r);

        track.setStrokeWidth(stroke);
        c.drawArc(oval, START, SWEEP, false, track);

        // Ticks and numbers: a major tick and number every `step`, four minor ticks between.
        int majors = Math.round(max / step);
        int minors = 4;
        for (int i = 0; i <= majors * (minors + 1); i++) {
            boolean major = i % (minors + 1) == 0;
            float ang = START + SWEEP * i / (float) (majors * (minors + 1));
            float r1 = r - stroke * 3f, r2 = r1 - (major ? size * 0.05f : size * 0.022f);
            tick.setColor(major ? textColor : dimColor);
            tick.setStrokeWidth(major ? stroke * 1.6f : stroke);
            double a = rad(ang);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, tick);
            if (major) {
                num.setTextSize(size * 0.042f);
                float rn = r2 - size * 0.05f;
                String s = String.valueOf(Math.round(i / (minors + 1) * step));
                c.drawText(s, cx + (float) Math.cos(a) * rn,
                        cy + (float) Math.sin(a) * rn + num.getTextSize() * 0.35f, num);
            }
        }

        // Ease toward the target value for smooth motion between samples.
        if (!Float.isNaN(target) && !Float.isNaN(shown) && Math.abs(target - shown) > max * 0.002f) {
            shown += (target - shown) * 0.2f;
            postInvalidateOnAnimation();
        } else {
            shown = target;
        }

        float frac = Float.isNaN(shown) ? 0f : Math.max(0f, Math.min(1f, shown / max));
        arc.setStrokeWidth(stroke * 2f);
        if (frac > 0.001f) c.drawArc(oval, START, SWEEP * frac, false, arc);

        // Needle and hub
        double a = rad(START + SWEEP * frac);
        float tail = size * 0.06f, tip = r * 0.72f;
        needle.setStrokeWidth(stroke * 2.2f);
        c.drawLine(cx - (float) Math.cos(a) * tail, cy - (float) Math.sin(a) * tail,
                cx + (float) Math.cos(a) * tip, cy + (float) Math.sin(a) * tip, needle);
        float hr = size * 0.03f;
        c.drawCircle(cx, cy, hr, hub);
        hubRing.setStrokeWidth(stroke * 1.4f);
        c.drawCircle(cx, cy, hr, hubRing);

        // Digital readout under the hub
        String text = Float.isNaN(shown) ? "--" : String.format(Locale.US, "%.0f", shown);
        value.setTextSize(size * 0.15f);
        c.drawText(text, cx, cy + r * 0.52f, value);
        label.setTextSize(size * 0.045f);
        c.drawText(unit, cx, cy + r * 0.52f + size * 0.07f, label);
    }
}

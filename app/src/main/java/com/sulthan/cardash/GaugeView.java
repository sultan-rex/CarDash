package com.sulthan.cardash;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** A thin 240° arc gauge with a smoothly animated arc and a large light digital readout. */
public class GaugeView extends View {
    private static final float START = 150f;
    private static final float SWEEP = 240f;

    private String title = "";
    private String unit = "";
    private float min = 0, max = 100, warn = Float.NaN;
    private int decimals = 0;
    private int majorTicks = 6;

    private float target = Float.NaN;
    private float shown = Float.NaN;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final int accent, warnColor, dim;

    public GaugeView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        TypedArray a = ctx.obtainStyledAttributes(attrs, R.styleable.GaugeView);
        title = str(a.getString(R.styleable.GaugeView_title));
        unit = str(a.getString(R.styleable.GaugeView_unit));
        min = a.getFloat(R.styleable.GaugeView_minValue, min);
        max = a.getFloat(R.styleable.GaugeView_maxValue, max);
        warn = a.getFloat(R.styleable.GaugeView_warnValue, Float.NaN);
        decimals = a.getInt(R.styleable.GaugeView_decimals, 0);
        majorTicks = a.getInt(R.styleable.GaugeView_majorTicks, majorTicks);
        a.recycle();

        accent = color(ctx, R.color.accent);
        warnColor = color(ctx, R.color.warn);
        dim = color(ctx, R.color.text_dim);

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(color(ctx, R.color.track));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        valueText.setColor(color(ctx, R.color.text));
        valueText.setTextAlign(Paint.Align.CENTER);
        valueText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        labelText.setColor(dim);
        labelText.setTextAlign(Paint.Align.CENTER);
    }

    private static String str(String s) { return s == null ? "" : s; }

    @SuppressWarnings("deprecation") // Context.getColor needs API 23; this tablet is API 22.
    static int color(Context ctx, int id) { return ctx.getResources().getColor(id); }

    public void setRange(float min, float max, float warn, String unit) {
        this.min = min;
        this.max = max;
        this.warn = warn;
        this.unit = unit;
        invalidate();
    }

    /** NaN shows "--". */
    public void setValue(double v) {
        target = (float) v;
        if (Float.isNaN(shown) || Float.isNaN(target)) shown = target;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        // The arc's open bottom ends at 0.5r below centre; the title sits just under that.
        float size = Math.min(w - 16, (h - 16) / 0.85f);
        float stroke = size * 0.03f;
        float r = size / 2 - stroke;
        float contentH = stroke / 2 + r * 1.5f + size * 0.15f;
        float cx = w / 2, cy = (h - contentH) / 2 + stroke / 2 + r;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        c.drawArc(oval, START, SWEEP, false, track);

        // Ease toward the target value for smooth motion between OBD samples.
        if (!Float.isNaN(target) && !Float.isNaN(shown) && Math.abs(target - shown) > (max - min) * 0.002f) {
            shown += (target - shown) * 0.25f;
            postInvalidateOnAnimation();
        } else {
            shown = target;
        }

        String text = "--";
        if (!Float.isNaN(shown)) {
            float frac = Math.max(0, Math.min(1, (shown - min) / (max - min)));
            boolean alarm = !Float.isNaN(warn) && shown >= warn;
            arc.setColor(alarm ? warnColor : accent);
            if (frac > 0.001f) c.drawArc(oval, START, SWEEP * frac, false, arc);
            text = String.format(Locale.US, "%." + decimals + "f", shown);
        }

        valueText.setTextSize(size * 0.24f);
        c.drawText(text, cx, cy + size * 0.07f, valueText);
        labelText.setTextSize(size * 0.08f);
        c.drawText(unit, cx, cy + size * 0.2f, labelText);
        labelText.setTextSize(size * 0.095f);
        c.drawText(title, cx, cy + r * 0.5f + size * 0.12f, labelText);
    }
}

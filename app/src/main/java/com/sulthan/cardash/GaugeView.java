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

/**
 * A 240° gauge in the Horizon style: a thick arc that fills teal, turning amber as it nears the
 * warning value and red past it, with a glowing tip and a large thin readout.
 */
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

    private final Paint valueText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final ArcStyle style;
    private final int dim;

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

        dim = color(ctx, R.color.text_dim);
        style = new ArcStyle(ctx);

        valueText.setColor(color(ctx, R.color.text));
        valueText.setTextAlign(Paint.Align.CENTER);
        valueText.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        labelText.setColor(dim);
        labelText.setTextAlign(Paint.Align.CENTER);
        labelText.setLetterSpacing(0.1f);
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
        float stroke = size * 0.065f;
        float r = size / 2 - stroke;
        float contentH = stroke / 2 + r * 1.5f + size * 0.15f;
        float cx = w / 2, cy = (h - contentH) / 2 + stroke / 2 + r;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        // Ease toward the target value for smooth motion between OBD samples.
        if (!Float.isNaN(target) && !Float.isNaN(shown) && Math.abs(target - shown) > (max - min) * 0.002f) {
            shown += (target - shown) * 0.25f;
            postInvalidateOnAnimation();
        } else {
            shown = target;
        }

        // Amber from a little before the warning value, red at it; no warning value stays teal.
        float redAt = Float.isNaN(warn) ? Float.NaN : (warn - min) / (max - min);
        float amberAt = Float.isNaN(redAt) ? Float.NaN : redAt - 0.12f;
        float frac = Float.isNaN(shown) ? Float.NaN : Math.max(0, Math.min(1, (shown - min) / (max - min)));
        style.setStroke(stroke);
        style.colours(cx, cy, START, SWEEP, amberAt, redAt);
        style.draw(c, oval, START, SWEEP, frac, amberAt, redAt);
        String text = Float.isNaN(shown) ? "--" : String.format(Locale.US, "%." + decimals + "f", shown);

        valueText.setTextSize(size * 0.24f);
        c.drawText(text, cx, cy + size * 0.07f, valueText);
        labelText.setTextSize(size * 0.08f);
        c.drawText(unit, cx, cy + size * 0.2f, labelText);
        labelText.setTextSize(size * 0.085f);
        c.drawText(title.toUpperCase(Locale.US), cx, cy + r * 0.5f + size * 0.12f, labelText);
    }
}

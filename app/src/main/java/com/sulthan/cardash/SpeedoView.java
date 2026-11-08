package com.sulthan.cardash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** The speedometer in the Horizon style: a thick 270° arc that fills teal to red, and a large thin number. */
public class SpeedoView extends View {
    private static final float START = 135f;
    private static final float SWEEP = 270f;

    private float max = 240, step = 20;
    private String unit = "km/h";
    private float target = Float.NaN;
    private float shown = Float.NaN;

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final ArcStyle style;
    private final int textColor, dimColor;

    public SpeedoView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        textColor = GaugeView.color(ctx, R.color.text);
        dimColor = GaugeView.color(ctx, R.color.text_dim);
        style = new ArcStyle(ctx);

        tick.setStyle(Paint.Style.STROKE);
        num.setColor(dimColor);
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setColor(textColor);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        label.setColor(dimColor);
        label.setTextAlign(Paint.Align.CENTER);
        label.setLetterSpacing(0.12f);
    }

    /** max and step are in the displayed unit, e.g. 240/20 for km/h or 160/20 for mph. */
    public void setRange(float max, float step, String unit) {
        this.max = max;
        this.step = step;
        this.unit = unit;
        invalidate();
    }

    /** The unit line under the number, e.g. "km/h · OBD". */
    public void setUnit(String unit) {
        if (unit.equals(this.unit)) return;
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
        float cx = getWidth() / 2f, cy = getHeight() / 2f + size * 0.04f;
        float stroke = size * 0.045f;
        float r = size / 2f - stroke;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        // Major ticks and numbers just inside the arc (light, like the home screen's horizon).
        int majors = Math.round(max / step);
        tick.setColor(dimColor);
        tick.setStrokeWidth(Math.max(2f, size * 0.006f));
        num.setTextSize(size * 0.045f);
        for (int i = 0; i <= majors; i++) {
            double a = rad(START + SWEEP * i / (float) majors);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float r1 = r - stroke * 0.9f, r2 = r1 - size * 0.025f, rn = r2 - size * 0.05f;
            c.drawLine(cx + cos * r1, cy + sin * r1, cx + cos * r2, cy + sin * r2, tick);
            c.drawText(String.valueOf(Math.round(i * step)), cx + cos * rn, cy + sin * rn + num.getTextSize() * 0.35f, num);
        }

        // Ease toward the target value for smooth motion between samples.
        if (!Float.isNaN(target) && !Float.isNaN(shown) && Math.abs(target - shown) > max * 0.002f) {
            shown += (target - shown) * 0.2f;
            postInvalidateOnAnimation();
        } else {
            shown = target;
        }

        float frac = Float.isNaN(shown) ? Float.NaN : Math.max(0f, Math.min(1f, shown / max));
        style.setStroke(stroke);
        style.colours(cx, cy, START, SWEEP, 0.5f, 0.75f);
        style.draw(c, oval, START, SWEEP, frac, 0.5f, 0.75f);

        // The number, large and thin, in the middle; the unit line under it.
        String text = Float.isNaN(shown) ? "--" : String.format(Locale.US, "%.0f", shown);
        value.setTextSize(size * 0.26f);
        c.drawText(text, cx, cy + size * 0.08f, value);
        label.setTextSize(size * 0.045f);
        c.drawText(unit.toUpperCase(Locale.US), cx, cy + size * 0.17f, label);
    }
}

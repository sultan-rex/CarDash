package com.sulthan.cardash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * The "horizon": a wide, shallow arc across the top of the home screen that fills with the speed.
 * Its colour runs from teal through amber to red as the speed rises, with a bright dot at the tip.
 * The speed number itself is a normal TextView laid over the middle of the arc.
 *
 * Kept cheap for a slow tablet: one gradient built per size change, and the fill eases toward
 * the new speed over a few frames only when the speed changes.
 */
public class HorizonView extends View {
    /** The arc spans this many degrees, centred on straight up. */
    private static final float SWEEP = 140f;
    private static final float START = 270f - SWEEP / 2f;

    private float max = 160, step = 40;
    private float target = Float.NaN, shown = 0;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final int accent, amber, red;
    private float cx, cy, r, stroke;

    public HorizonView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        accent = GaugeView.color(ctx, R.color.accent);
        amber = 0xFFFFB547;
        red = GaugeView.color(ctx, R.color.warn);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(GaugeView.color(ctx, R.color.track));
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeCap(Paint.Cap.ROUND);
        tip.setColor(Color.WHITE);
        tipGlow.setStyle(Paint.Style.FILL);
        tick.setColor(GaugeView.color(ctx, R.color.text_dim));
        tick.setStrokeCap(Paint.Cap.ROUND);
        num.setColor(GaugeView.color(ctx, R.color.text_dim));
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    }

    /** Full scale and label step in the displayed unit (160/40 for km/h, 100/20 for mph). */
    public void setRange(float max, float step) {
        this.max = max;
        this.step = step;
        invalidate();
    }

    /** Speed in the displayed unit; NaN empties the arc. */
    public void setValue(double v) {
        float t = Double.isNaN(v) ? Float.NaN : (float) Math.max(0, Math.min(v, max));
        if (t == target || (Float.isNaN(t) && Float.isNaN(target))) return;
        target = t;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float d = getResources().getDisplayMetrics().density;
        stroke = 14 * d;
        // A circle much wider than the view, so only its gentle top shows: the horizon.
        float half = SWEEP / 2f * (float) Math.PI / 180f;
        r = (w / 2f - stroke - 8 * d) / (float) Math.sin(half);
        cx = w / 2f;
        cy = stroke + 10 * d + r;
        // The arc's lowest ends sit at cy - r*cos(half); keep them inside the view.
        float endsY = cy - r * (float) Math.cos(half);
        if (endsY > h - 18 * d) {
            r = (h - stroke - 28 * d) / (1 - (float) Math.cos(half));
            cy = stroke + 10 * d + r;
        }
        oval.set(cx - r, cy - r, cx + r, cy + r);
        track.setStrokeWidth(stroke);
        fill.setStrokeWidth(stroke);
        tick.setStrokeWidth(2 * d);
        num.setTextSize(13 * d);

        // Colour along the arc: teal for the first half, amber by ~80 %, red at the end.
        fill.setShader(new SweepGradient(cx, cy,
                new int[]{red, accent, accent, amber, red},
                new float[]{0f, START / 360f, (START + SWEEP * 0.55f) / 360f, (START + SWEEP * 0.8f) / 360f, 1f}));
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        c.drawArc(oval, START, SWEEP, false, track);

        // Ticks and labels just inside the arc.
        for (float v = 0; v <= max + 0.01f; v += step) {
            double a = Math.toRadians(START + SWEEP * v / max);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float r1 = r - stroke / 2f - 6 * d, r2 = r1 - 8 * d;
            c.drawLine(cx + r1 * cos, cy + r1 * sin, cx + r2 * cos, cy + r2 * sin, tick);
            float rl = r2 - 12 * d;
            c.drawText(String.format(Locale.US, "%.0f", v), cx + rl * cos, cy + rl * sin + 4 * d, num);
        }

        // Ease the fill toward the target.
        float goal = Float.isNaN(target) ? 0 : target;
        float diff = goal - shown;
        if (Math.abs(diff) > max * 0.002f) {
            shown += diff * 0.25f;
            postInvalidateOnAnimation();
        } else {
            shown = goal;
        }
        if (shown <= 0.0001f || Float.isNaN(target)) return;
        float sweep = SWEEP * shown / max;
        c.drawArc(oval, START, sweep, false, fill);

        double a = Math.toRadians(START + sweep);
        float tx = cx + r * (float) Math.cos(a), ty = cy + r * (float) Math.sin(a);
        float frac = shown / max;
        int col = frac < 0.55f ? accent : frac < 0.8f ? amber : red;
        tipGlow.setColor((col & 0x00FFFFFF) | 0x55000000);
        c.drawCircle(tx, ty, stroke * 0.95f, tipGlow);
        c.drawCircle(tx, ty, stroke * 0.38f, tip);
    }
}

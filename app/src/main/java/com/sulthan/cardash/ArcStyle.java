package com.sulthan.cardash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;

/**
 * The Horizon arc look shared by every gauge: a thick round-capped track, a fill that runs teal,
 * then amber, then red along the arc, and a white dot with a soft glow at the tip.
 */
final class ArcStyle {
    static final int AMBER = 0xFFFFB547;

    final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    final int accent, red;
    /** Shader cache key: rebuilt only when the geometry or colour stops change. */
    private float sCx = Float.NaN, sCy, sStart, sSweep, sAmber, sRed;

    ArcStyle(Context ctx) {
        accent = GaugeView.color(ctx, R.color.accent);
        red = GaugeView.color(ctx, R.color.warn);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(GaugeView.color(ctx, R.color.track));
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeCap(Paint.Cap.ROUND);
        tip.setColor(Color.WHITE);
        glow.setStyle(Paint.Style.FILL);
    }

    void setStroke(float w) {
        track.setStrokeWidth(w);
        fill.setStrokeWidth(w);
    }

    /**
     * Sets the fill colours along an arc from [start] through [sweep] degrees. [amberAt] and
     * [redAt] are fractions of the arc (0..1) where amber and red are reached; NaN keeps it teal.
     */
    void colours(float cx, float cy, float start, float sweep, float amberAt, float redAt) {
        if (cx == sCx && cy == sCy && start == sStart && sweep == sSweep
                && same(amberAt, sAmber) && same(redAt, sRed)) return;
        sCx = cx; sCy = cy; sStart = start; sSweep = sweep; sAmber = amberAt; sRed = redAt;
        if (Float.isNaN(amberAt)) {
            fill.setShader(null);
            fill.setColor(accent);
            return;
        }
        // SweepGradient runs 0..360 from 3 o'clock; build it from 0 and rotate it to the arc's start,
        // so arcs that cross 3 o'clock (like the speedometer's) work too.
        // The gradient starts a little before the arc, so the round cap at the start is teal, not
        // the red from the far end of the colour wheel.
        float lead = 12f, k = sweep / 360f, o = lead / 360f;
        float[] pos = {0f, o + k * Math.max(0, amberAt - 0.2f), o + k * amberAt, o + k * Math.min(1, redAt), 1f};
        for (int i = 1; i < pos.length; i++) pos[i] = Math.min(1f, Math.max(pos[i], pos[i - 1]));
        int[] col = {accent, accent, AMBER, red, red};
        SweepGradient g = new SweepGradient(cx, cy, col, pos);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRotate(start - lead, cx, cy);
        g.setLocalMatrix(m);
        fill.setShader(g);
    }

    /** Draws the track, the fill up to [frac] and the glowing tip. */
    void draw(Canvas c, RectF oval, float start, float sweep, float frac, float amberAt, float redAt) {
        c.drawArc(oval, start, sweep, false, track);
        if (Float.isNaN(frac) || frac <= 0.001f) return;
        if (fill.getShader() == null) fill.setColor(colourAt(frac, amberAt, redAt));
        float s = sweep * Math.min(1, frac);
        c.drawArc(oval, start, s, false, fill);

        float r = oval.width() / 2f, cx = oval.centerX(), cy = oval.centerY();
        double a = Math.toRadians(start + s);
        float tx = cx + r * (float) Math.cos(a), ty = cy + r * (float) Math.sin(a);
        int col = colourAt(frac, amberAt, redAt);
        glow.setColor((col & 0x00FFFFFF) | 0x55000000);
        float w = fill.getStrokeWidth();
        c.drawCircle(tx, ty, w * 0.95f, glow);
        c.drawCircle(tx, ty, w * 0.38f, tip);
    }

    int colourAt(float frac, float amberAt, float redAt) {
        if (Float.isNaN(amberAt) || frac < amberAt - 0.05f) return accent;
        return frac < redAt ? AMBER : red;
    }

    private static boolean same(float a, float b) {
        return a == b || (Float.isNaN(a) && Float.isNaN(b));
    }
}

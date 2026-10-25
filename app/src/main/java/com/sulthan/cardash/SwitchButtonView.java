package com.sulthan.cardash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * The switch button: a quarter disc docked in a screen corner, or a round button while it is
 * being dragged. The window is a square of side [size]; the disc is drawn into it.
 */
final class SwitchButtonView extends View {
    static final int TOP_LEFT = 0, TOP_RIGHT = 1, BOTTOM_LEFT = 2, BOTTOM_RIGHT = 3;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float iconSize;
    private int corner = TOP_RIGHT;
    private boolean floating;
    private Drawable icon;

    SwitchButtonView(Context ctx, float density) {
        super(ctx);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xD90D0D0D);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        stroke.setColor(0x40FFFFFF);
        iconSize = 30 * density;
    }

    void setCorner(int corner) {
        this.corner = corner;
        invalidate();
    }

    int corner() { return corner; }

    boolean isFloating() { return floating; }

    /** Floating: a round button, used while dragging. Docked: a quarter disc in the corner. */
    void setFloating(boolean floating) {
        this.floating = floating;
        invalidate();
    }

    void setIcon(int resId) {
        icon = getResources().getDrawable(resId).mutate();
        icon.setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
        invalidate();
    }

    /** Whether a touch at (x, y) in this view lands on the visible shape. */
    boolean hits(float x, float y) {
        float s = getWidth();
        if (floating) {
            float c = s / 2f, r = s * 0.4f;
            return (x - c) * (x - c) + (y - c) * (y - c) <= r * r;
        }
        float cx = (corner == TOP_LEFT || corner == BOTTOM_LEFT) ? 0 : s;
        float cy = (corner == TOP_LEFT || corner == TOP_RIGHT) ? 0 : s;
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= s * s;
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = getWidth();
        float cx, cy, r, ix, iy;
        if (floating) {
            cx = cy = s / 2f;
            r = s * 0.4f;
            ix = iy = s / 2f;
        } else {
            boolean left = corner == TOP_LEFT || corner == BOTTOM_LEFT;
            boolean top = corner == TOP_LEFT || corner == TOP_RIGHT;
            cx = left ? 0 : s;
            cy = top ? 0 : s;
            r = s - stroke.getStrokeWidth();
            ix = left ? s * 0.4f : s * 0.6f;
            iy = top ? s * 0.4f : s * 0.6f;
        }
        c.drawCircle(cx, cy, r, fill);
        c.drawCircle(cx, cy, r, stroke);
        if (icon != null) {
            int h = (int) (iconSize / 2);
            icon.setBounds((int) ix - h, (int) iy - h, (int) ix + h, (int) iy + h);
            icon.draw(c);
        }
    }
}

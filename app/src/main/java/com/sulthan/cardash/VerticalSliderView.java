package com.sulthan.cardash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A minimal vertical slider: a thin track, a filled part below the handle, and a round handle.
 * Bottom is zero, top is the maximum. Touching it keeps the pager from turning the page.
 */
public class VerticalSliderView extends View {
    interface OnChange { void onChange(int progress, boolean fromUser); }
    interface OnTouchState { void onTouching(boolean touching); }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private int max = 100, progress;
    private boolean touching;
    private OnChange onChange;
    private OnTouchState onTouchState;

    public VerticalSliderView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        density = ctx.getResources().getDisplayMetrics().density;
        track.setColor(GaugeView.color(ctx, R.color.track));
        fill.setColor(GaugeView.color(ctx, R.color.text_dim));
        thumb.setColor(GaugeView.color(ctx, R.color.text));
    }

    void setOnChange(OnChange l) { onChange = l; }

    void setOnTouchState(OnTouchState l) { onTouchState = l; }

    void setMax(int max) {
        this.max = Math.max(1, max);
        invalidate();
    }

    void setProgress(int p) {
        progress = Math.max(0, Math.min(max, p));
        invalidate();
    }

    int getProgress() { return progress; }

    private float pad() { return 14 * density; }

    private float yFor(int p) {
        float top = pad(), bottom = getHeight() - pad();
        return bottom - (bottom - top) * p / max;
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, half = 3 * density;
        float top = pad(), bottom = getHeight() - pad();
        rect.set(cx - half, top, cx + half, bottom);
        c.drawRoundRect(rect, half, half, track);
        float y = yFor(progress);
        rect.set(cx - half, y, cx + half, bottom);
        c.drawRoundRect(rect, half, half, fill);
        c.drawCircle(cx, y, (touching ? 12 : 10) * density, thumb);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                touching = true;
                if (onTouchState != null) onTouchState.onTouching(true);
                update(e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                update(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                if (onTouchState != null) onTouchState.onTouching(false);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void update(float y) {
        float top = pad(), bottom = getHeight() - pad();
        float f = (bottom - y) / (bottom - top);
        int p = Math.round(Math.max(0f, Math.min(1f, f)) * max);
        if (p != progress || !touching) {
            progress = p;
            if (onChange != null) onChange.onChange(p, true);
        }
        invalidate();
    }
}

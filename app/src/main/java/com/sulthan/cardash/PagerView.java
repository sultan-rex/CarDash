package com.sulthan.cardash;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

/**
 * A minimal horizontal pager: a HorizontalScrollView whose single child row holds full-width
 * pages, and which snaps to a page when the finger lifts. Unlike ViewPager every page exists
 * from inflation, so findViewById works straight away.
 */
public class PagerView extends HorizontalScrollView {
    public interface OnPageChange { void onPage(int page); }

    private int page;
    private int flingDir;
    private OnPageChange listener;

    private final GestureDetector detector = new GestureDetector(getContext(),
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (Math.abs(vx) > 400 && Math.abs(vx) > Math.abs(vy)) flingDir = vx < 0 ? 1 : -1;
                    return false;
                }
            });

    public PagerView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void setOnPageChange(OnPageChange l) { listener = l; }

    public int page() { return page; }

    private int pageCount() {
        return getChildCount() == 0 ? 0 : ((ViewGroup) getChildAt(0)).getChildCount();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (getChildCount() > 0 && w > 0) {
            ViewGroup row = (ViewGroup) getChildAt(0);
            for (int i = 0; i < row.getChildCount(); i++) {
                View p = row.getChildAt(i);
                if (p.getLayoutParams().width != w) p.getLayoutParams().width = w;
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        scrollTo(page * w, 0);
    }

    /** The default fling would coast past a page; snapping decides instead. */
    @Override
    public void fling(int velocityX) { }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        detector.onTouchEvent(ev);
        boolean handled = super.onTouchEvent(ev);
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            int w = Math.max(1, getWidth());
            int target = flingDir != 0 ? page + flingDir : Math.round(getScrollX() / (float) w);
            flingDir = 0;
            goTo(target);
        }
        return handled;
    }

    public void goTo(int target) {
        int last = Math.max(0, pageCount() - 1);
        target = Math.max(0, Math.min(last, target));
        smoothScrollTo(target * getWidth(), 0);
        if (target != page) {
            page = target;
            if (listener != null) listener.onPage(page);
        }
    }
}

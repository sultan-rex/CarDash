package com.sulthan.cardash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/** A month grid. Days with a drive are tinted by distance and show their kilometres. */
public class CalendarGridView extends View {
    interface OnDay { void onDay(int dayOfMonth); }

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cell = new RectF();
    private final int textColor, dimColor, accent;

    private int year, month0, selected = -1;
    private boolean imperial;
    private OnDay listener;
    private DriveLog log;
    private double maxKm;

    public CalendarGridView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        textColor = GaugeView.color(ctx, R.color.text);
        dimColor = GaugeView.color(ctx, R.color.text_dim);
        accent = GaugeView.color(ctx, R.color.accent);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        small.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tint.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(accent);
    }

    void setOnDay(OnDay l) { listener = l; }

    void setLog(DriveLog log, boolean imperial) {
        this.log = log;
        this.imperial = imperial;
        invalidate();
    }

    void setMonth(int year, int month0) {
        this.year = year;
        this.month0 = month0;
        this.selected = -1;
        recomputeMax();
        invalidate();
    }

    void setSelected(int day) {
        selected = day;
        invalidate();
    }

    private void recomputeMax() {
        maxKm = 0;
        if (log == null) return;
        int n = daysInMonth();
        for (int d = 1; d <= n; d++) {
            DriveLog.Day day = log.day(DriveLog.key(year, month0, d));
            if (day != null && day.km > maxKm) maxKm = day.km;
        }
    }

    private int daysInMonth() {
        Calendar c = Calendar.getInstance();
        c.set(year, month0, 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /** Column of the first of the month, with the week starting on the locale's first day. */
    private int leadingBlanks() {
        Calendar c = Calendar.getInstance();
        c.set(year, month0, 1);
        int dow = c.get(Calendar.DAY_OF_WEEK); // 1 = Sunday
        int first = c.getFirstDayOfWeek();
        return (dow - first + 7) % 7;
    }

    private String[] weekdayLetters() {
        String[] out = new String[7];
        Calendar c = Calendar.getInstance();
        int first = c.getFirstDayOfWeek();
        String letters = "SMTWTFS"; // starting Sunday
        for (int i = 0; i < 7; i++) out[i] = String.valueOf(letters.charAt((first - 1 + i) % 7));
        return out;
    }

    private int rows() { return (leadingBlanks() + daysInMonth() + 6) / 7; }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float headerH = h * 0.09f;
        int rows = rows();
        float cw = w / 7f, ch = (h - headerH) / rows;
        text.setColor(dimColor);
        text.setTextSize(Math.min(cw, ch) * 0.2f);
        String[] letters = weekdayLetters();
        for (int i = 0; i < 7; i++) c.drawText(letters[i], cw * i + cw / 2, headerH * 0.7f, text);

        int blanks = leadingBlanks(), n = daysInMonth();
        Calendar today = Calendar.getInstance();
        boolean thisMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month0;
        ring.setStrokeWidth(getResources().getDisplayMetrics().density * 1.5f);
        for (int d = 1; d <= n; d++) {
            int idx = blanks + d - 1;
            int col = idx % 7, row = idx / 7;
            float pad = Math.min(cw, ch) * 0.06f;
            cell.set(cw * col + pad, headerH + ch * row + pad, cw * (col + 1) - pad, headerH + ch * (row + 1) - pad);
            DriveLog.Day day = log == null ? null : log.day(DriveLog.key(year, month0, d));
            boolean drove = day != null && day.km > 0.05;
            float r = Math.min(cw, ch) * 0.16f;
            if (drove) {
                float strength = maxKm > 0 ? (float) Math.min(1.0, 0.25 + 0.75 * day.km / maxKm) : 0.4f;
                tint.setColor((accent & 0x00FFFFFF) | ((int) (strength * 150) << 24));
                c.drawRoundRect(cell, r, r, tint);
            }
            if (d == selected) {
                ring.setColor(accent);
                c.drawRoundRect(cell, r, r, ring);
            } else if (thisMonth && d == today.get(Calendar.DAY_OF_MONTH)) {
                ring.setColor(0x66FFFFFF);
                c.drawRoundRect(cell, r, r, ring);
            }
            text.setColor(drove ? textColor : dimColor);
            text.setTextSize(Math.min(cw, ch) * 0.3f);
            float cx = cell.centerX();
            c.drawText(String.valueOf(d), cx, drove ? cell.top + ch * 0.45f : cell.centerY() + text.getTextSize() * 0.35f, text);
            if (drove) {
                small.setColor(textColor);
                small.setTextSize(Math.min(cw, ch) * 0.2f);
                double v = imperial ? day.km * 0.621371 : day.km;
                c.drawText(String.format(Locale.getDefault(), v >= 100 ? "%.0f" : "%.1f", v), cx, cell.bottom - ch * 0.12f, small);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            float w = getWidth(), h = getHeight();
            float headerH = h * 0.09f;
            if (e.getY() < headerH) return true;
            int col = (int) (e.getX() / (w / 7f));
            int row = (int) ((e.getY() - headerH) / ((h - headerH) / rows()));
            int d = row * 7 + col - leadingBlanks() + 1;
            if (d >= 1 && d <= daysInMonth() && listener != null) listener.onDay(d);
        }
        return true;
    }
}

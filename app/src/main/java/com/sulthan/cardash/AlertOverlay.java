package com.sulthan.cardash;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A warning banner across the top of the screen, above every app (Android Auto included), with a
 * short chime. It covers only the top strip, never the whole screen, so the map stays usable while
 * driving. Tap it to close it; a banner given a timeout also closes by itself.
 */
final class AlertOverlay {
    static final int RED = 0xFFFF5A5F;
    static final int AMBER = 0xFFFFC857;
    static final int TEAL = 0xFF2EC4B6;

    private static AlertOverlay instance;

    private final Context app;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private View shown;
    private String shownTag;
    private final Runnable hide = this::dismiss;

    static synchronized AlertOverlay get(Context ctx) {
        if (instance == null) instance = new AlertOverlay(ctx.getApplicationContext());
        return instance;
    }

    private AlertOverlay(Context app) {
        this.app = app;
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Shows a banner (replacing any other). [tag] names it so [dismiss(String)] only closes the
     * matching one. [autoHideMs] 0 keeps it until tapped.
     */
    void show(String tag, String title, String detail, int color, boolean chime, long autoHideMs) {
        dismiss();
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(app)) return;

        LinearLayout box = new LinearLayout(app);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(dp(22), pad, dp(22), pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0101010);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(3), color);
        box.setBackground(bg);

        TextView t = new TextView(app);
        t.setText(title);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        box.addView(t);

        TextView d = new TextView(app);
        d.setText(detail + "\nTap to close");
        d.setTextColor(0xFFF5F5F5);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        d.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        box.addView(d);

        box.setOnClickListener(v -> dismiss());

        @SuppressWarnings("deprecation")
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(16);
        try {
            wm.addView(box, lp);
        } catch (RuntimeException e) {
            return; // overlay permission missing: nothing we can draw
        }
        shown = box;
        shownTag = tag;
        if (chime) chime();
        if (autoHideMs > 0) ui.postDelayed(hide, autoHideMs);
    }

    void dismiss(String tag) {
        if (tag.equals(shownTag)) dismiss();
    }

    void dismiss() {
        ui.removeCallbacks(hide);
        View v = shown;
        shown = null;
        shownTag = null;
        if (v != null) try { wm.removeView(v); } catch (RuntimeException ignored) { }
    }

    /** Three short beeps on the media stream, the one the volume slider sets. */
    private void chime() {
        final ToneGenerator tg;
        try {
            tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            return;
        }
        for (int i = 0; i < 3; i++) ui.postDelayed(() -> tg.startTone(ToneGenerator.TONE_PROP_BEEP, 180), i * 320L);
        ui.postDelayed(tg::release, 1200);
    }

    private int dp(int v) {
        return Math.round(v * app.getResources().getDisplayMetrics().density);
    }
}

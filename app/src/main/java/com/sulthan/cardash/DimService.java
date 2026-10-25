package com.sulthan.cardash;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

/**
 * A software dimmer: a see-through black layer over the whole screen, for going darker than the
 * tablet's lowest backlight setting (Android will not go below 10 here). The backlight stays at
 * its minimum; this only darkens the picture. The level is remembered, so it survives restarts.
 */
public class DimService extends Service {
    private static final String PREF_DARKNESS = "dim_darkness";
    /** Percent of black at the deepest setting: dark, but the screen can still be read. */
    static final int MAX_DARKNESS = 85;

    private WindowManager wm;
    private View layer;

    /** Darkness now, 0 (off) to MAX_DARKNESS. */
    static int darkness(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getInt(PREF_DARKNESS, 0);
    }

    static void setDarkness(Context ctx, int percent) {
        int p = Math.max(0, Math.min(MAX_DARKNESS, percent));
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putInt(PREF_DARKNESS, p).apply();
        sync(ctx);
    }

    /** Runs the service while there is something to show, and stops it when the screen is not dimmed. */
    static void sync(Context ctx) {
        Intent i = new Intent(ctx, DimService.class);
        if (darkness(ctx) > 0) ctx.startService(i);
        else ctx.stopService(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        int percent = sp.getInt(PREF_DARKNESS, 0);
        boolean canDraw = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        if (percent <= 0 || !canDraw) {
            remove();
            stopSelf();
            return START_NOT_STICKY;
        }
        int color = Color.argb(Math.round(percent * 255 / 100f), 0, 0, 0);
        if (layer == null) add(color);
        else layer.setBackgroundColor(color);
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void add(int color) {
        layer = new View(this);
        layer.setBackgroundColor(color);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        try {
            wm.addView(layer, lp);
        } catch (RuntimeException e) {
            layer = null;
        }
    }

    private void remove() {
        if (layer == null) return;
        try { wm.removeView(layer); } catch (RuntimeException ignored) { }
        layer = null;
    }

    @Override
    public void onDestroy() {
        remove();
        super.onDestroy();
    }
}

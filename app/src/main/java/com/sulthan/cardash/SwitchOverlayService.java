package com.sulthan.cardash;

import android.app.ActivityOptions;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.andrerinas.openheadunit.host.HostBridge;

/**
 * A small button floating above every screen while a phone is connected over Android Auto. It
 * flips between the Android Auto screen and CarDash; both stay alive, so the switch is a fade.
 * Drag it to move it.
 */
public class SwitchOverlayService extends Service {
    static final String PREF_ENABLED = "overlay_switch";
    private static final String PREF_CORNER = "overlay_switch_corner";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private SwitchButtonView button;
    private WindowManager.LayoutParams lp;
    private SharedPreferences prefs;
    private boolean showingHome;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            update();
            ui.postDelayed(this, 700);
        }
    };

    /** Starts or stops the service to match the setting. */
    static void sync(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        Intent i = new Intent(ctx, SwitchOverlayService.class);
        if (sp.getBoolean(PREF_ENABLED, true)) ctx.startService(i);
        else ctx.stopService(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ui.removeCallbacks(poll);
        ui.post(poll);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacks(poll);
        remove();
        super.onDestroy();
    }

    private boolean canDraw() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void update() {
        boolean enabled = prefs.getBoolean(PREF_ENABLED, true);
        if (!enabled) { stopSelf(); return; }
        boolean show = canDraw() && HostBridge.isConnected(this);
        if (!show) { remove(); return; }
        if (button == null) add();
        else keepDocked();
        boolean inAa = HostBridge.isProjectionForeground();
        if (button != null && showingHome != inAa) {
            showingHome = inAa;
            button.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                if (button == null) return;
                button.setIcon(showingHome ? R.drawable.ic_home : R.drawable.ic_car);
                button.animate().alpha(0.8f).setDuration(120).start();
            }).start();
        }
    }

    private int size;
    private int screenW, screenH;

    private void metrics() {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        size = (int) (88 * dm.density);
    }

    /** The window's top-left for a docked corner, from the current screen size. */
    private int cornerX(int corner) {
        return (corner == SwitchButtonView.TOP_LEFT || corner == SwitchButtonView.BOTTOM_LEFT) ? 0 : screenW - size;
    }

    private int cornerY(int corner) {
        return (corner == SwitchButtonView.TOP_LEFT || corner == SwitchButtonView.TOP_RIGHT) ? 0 : screenH - size;
    }

    @SuppressWarnings("deprecation")
    private void add() {
        metrics();
        float density = getResources().getDisplayMetrics().density;
        button = new SwitchButtonView(this, density);
        button.setIcon(R.drawable.ic_car);
        button.setCorner(prefs.getInt(PREF_CORNER, SwitchButtonView.BOTTOM_RIGHT));
        button.setAlpha(0.8f);
        showingHome = false;

        lp = new WindowManager.LayoutParams(size, size,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = cornerX(button.corner());
        lp.y = cornerY(button.corner());

        final int slop = (int) (8 * density);
        button.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, grabX, grabY;
            boolean dragging, accepted;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        accepted = button.hits(e.getX(), e.getY());
                        if (!accepted) return false;
                        downX = e.getRawX(); downY = e.getRawY();
                        grabX = e.getX(); grabY = e.getY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!accepted) return false;
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                            dragging = true;
                            button.setFloating(true);
                            button.setAlpha(0.95f);
                        }
                        if (dragging) {
                            // Follow the finger, keeping the button's centre under it.
                            lp.x = clamp((int) (e.getRawX() - size / 2f), 0, screenW - size);
                            lp.y = clamp((int) (e.getRawY() - size / 2f), 0, screenH - size);
                            try { wm.updateViewLayout(button, lp); } catch (RuntimeException ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!accepted) return false;
                        if (dragging) dock(e.getRawX(), e.getRawY());
                        else toggle();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) dock(e.getRawX(), e.getRawY());
                        return true;
                    default:
                        return false;
                }
            }
        });
        try {
            wm.addView(button, lp);
        } catch (RuntimeException e) {
            button = null;
        }
    }

    /** Re-seats a docked button after the screen turns, since the corners move with it. */
    private void keepDocked() {
        metrics();
        if (button == null || lp == null) return;
        boolean moving = false;
        if (button.isFloating() || moving) return;
        int x = cornerX(button.corner()), y = cornerY(button.corner());
        if (lp.x != x || lp.y != y || lp.width != size) {
            lp.x = x; lp.y = y; lp.width = size; lp.height = size;
            try { wm.updateViewLayout(button, lp); } catch (RuntimeException ignored) { }
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Glides into the corner nearest the finger and docks there as a quarter disc. */
    private void dock(float rawX, float rawY) {
        if (button == null) return;
        final boolean left = rawX < screenW / 2f, top = rawY < screenH / 2f;
        final int corner = top ? (left ? SwitchButtonView.TOP_LEFT : SwitchButtonView.TOP_RIGHT)
                : (left ? SwitchButtonView.BOTTOM_LEFT : SwitchButtonView.BOTTOM_RIGHT);
        final int fromX = lp.x, fromY = lp.y, toX = cornerX(corner), toY = cornerY(corner);
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(180);
        va.addUpdateListener(a -> {
            if (button == null) return;
            float t = (float) a.getAnimatedValue();
            lp.x = (int) (fromX + (toX - fromX) * t);
            lp.y = (int) (fromY + (toY - fromY) * t);
            try { wm.updateViewLayout(button, lp); } catch (RuntimeException ignored) { }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (button == null) return;
                button.setCorner(corner);
                button.setFloating(false);
                button.setAlpha(0.8f);
                prefs.edit().putInt(PREF_CORNER, corner).apply();
            }
        });
        va.start();
    }

    private void remove() {
        if (button == null) return;
        try { wm.removeView(button); } catch (RuntimeException ignored) { }
        button = null;
    }

    /** Fades to the other screen; each lives in its own task, so nothing is rebuilt. */
    private void toggle() {
        Intent i = HostBridge.isProjectionForeground()
                ? new Intent(this, MainActivity.class)
                : HostBridge.projectionIntent(this);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i, ActivityOptions.makeCustomAnimation(this,
                    android.R.anim.fade_in, android.R.anim.fade_out).toBundle());
        } catch (RuntimeException e) {
            startActivity(i);
        }
    }
}

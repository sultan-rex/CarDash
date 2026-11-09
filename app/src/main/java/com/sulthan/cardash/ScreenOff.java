package com.sulthan.cardash;

import android.app.Activity;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * The on-screen power button, for a tablet whose own power button is unreliable.
 *
 * While charging (in the car) it blacks the screen out instead of switching it off: the backlight
 * goes to its minimum behind a black layer, CarDash and Android Auto keep running, and a tap
 * anywhere wakes it. Switching the screen truly off there would leave only the faulty power button
 * to wake it, since the charger is already connected.
 *
 * On battery it switches the screen off for real (saving the battery), using Android's "device
 * admin" lock, which has to be allowed once. Plugging in the charger (starting the car) wakes it.
 */
final class ScreenOff {
    private static View black;

    private ScreenOff() {}

    /** Receiver Android needs for the device admin permission; it does nothing itself. */
    public static class Admin extends DeviceAdminReceiver { }

    static ComponentName admin(Context ctx) {
        return new ComponentName(ctx, Admin.class);
    }

    static boolean adminActive(Context ctx) {
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(admin(ctx));
    }

    static void press(Activity act) {
        if (BatterySaver.plugged(act)) {
            blackout(act);
            return;
        }
        if (adminActive(act)) {
            DevicePolicyManager dpm = (DevicePolicyManager) act.getSystemService(Context.DEVICE_POLICY_SERVICE);
            dpm.lockNow();
            return;
        }
        // Not allowed yet: ask once, and black the screen out meanwhile.
        Toast.makeText(act, "Allow CarDash to turn the screen off, then press the button again", Toast.LENGTH_LONG).show();
        act.startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(act))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Lets the on-screen power button switch the screen off. CarDash uses nothing else."));
    }

    /** A black layer over everything with the backlight at its lowest; a tap removes it. */
    static void blackout(Context ctx) {
        if (black != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) return;
        Context app = ctx.getApplicationContext();
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        View v = new View(app);
        v.setBackgroundColor(Color.BLACK);
        v.setOnClickListener(x -> wake(app));
        @SuppressWarnings("deprecation")
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE);
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF; // lowest the panel allows
        v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try {
            wm.addView(v, lp);
            black = v;
        } catch (RuntimeException ignored) { }
    }

    static boolean isBlack() { return black != null; }

    static void wake(Context ctx) {
        View v = black;
        black = null;
        if (v == null) return;
        WindowManager wm = (WindowManager) ctx.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        try { wm.removeView(v); } catch (RuntimeException ignored) { }
    }
}

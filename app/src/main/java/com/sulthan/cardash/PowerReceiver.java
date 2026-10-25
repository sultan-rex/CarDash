package com.sulthan.cardash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * Wakes the screen and opens CarDash when the tablet boots or starts charging, i.e. when the
 * car is switched on.
 */
public class PowerReceiver extends BroadcastReceiver {
    private static final long WAKE_MS = 10_000;

    @SuppressWarnings("deprecation") // The wake-up flags are the way to light the screen on Android 5.
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!new Prefs(ctx).launchOnPower()) return;

        // Light the screen first, then hold it for a few seconds while the activity comes up.
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "cardash:power");
            wl.acquire(WAKE_MS);
        }

        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        ctx.startActivity(i);
    }
}

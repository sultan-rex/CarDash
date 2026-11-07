package com.sulthan.cardash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * The car's ignition, as seen by the tablet: charging starts (or the tablet boots on the charger)
 * when the car is switched on, and stops when it is switched off.
 *
 * On: undo the battery saver, wake the screen and open CarDash. Off: start the battery saver's
 * countdown.
 */
public class PowerReceiver extends BroadcastReceiver {
    private static final long WAKE_MS = 10_000;

    @SuppressWarnings("deprecation") // The wake-up flags are the way to light the screen on Android 5.
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            BatterySaver.arm(ctx);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) && !BatterySaver.plugged(ctx)) {
            // Switched on by hand, on battery: count down from now. A saver state left over from
            // before the restart is undone first so nothing stays off for good.
            BatterySaver.wake(ctx);
            BatterySaver.arm(ctx);
            return;
        }
        BatterySaver.wake(ctx);
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

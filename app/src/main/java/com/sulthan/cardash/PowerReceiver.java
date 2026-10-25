package com.sulthan.cardash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Opens CarDash when the tablet boots or starts charging, i.e. when the car is switched on. */
public class PowerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!new Prefs(ctx).launchOnPower()) return;
        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        ctx.startActivity(i);
    }
}

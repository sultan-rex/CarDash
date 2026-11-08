package com.sulthan.cardash;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;

/**
 * When the car is switched off (charging stops) and the tablet has gone to sleep, waits a few
 * minutes and then turns off Wi-Fi, Bluetooth, GPS recording and the OBD connection so the battery
 * lasts. The tablet cannot switch itself back on once the battery is flat, so this matters. When
 * charging starts again, whatever it turned off is turned back on.
 *
 * While someone is using the tablet on battery (screen on) it does nothing and checks again later.
 */
public class BatterySaver extends BroadcastReceiver {
    static final String PREF_MINUTES = "saver_minutes";
    private static final String PREF_SAVING = "saver_active";
    private static final String PREF_WIFI_OFF = "saver_wifi_was_on";
    private static final String PREF_BT_OFF = "saver_bt_was_on";
    private static final String ACTION = "com.sulthan.cardash.SAVER_CHECK";

    /** Charging stopped, or CarDash went to the background on battery: (re)start the countdown. */
    static void arm(Context ctx) {
        int min = minutes(ctx);
        if (min <= 0 || plugged(ctx) || isSaving(ctx)) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = SystemClock.elapsedRealtime() + min * 60_000L;
        // Android 12+ allows exact alarms only with a special permission; a slightly late check is fine there.
        boolean exact = Build.VERSION.SDK_INT >= 19 && (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
        try {
            if (exact) am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(ctx));
            else am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(ctx));
        } catch (SecurityException e) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(ctx));
        }
    }

    /** Charging started: cancel the countdown and undo whatever the saver turned off. */
    static void wake(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(ctx));
        SharedPreferences sp = sp(ctx);
        if (!sp.getBoolean(PREF_SAVING, false)) return;
        if (sp.getBoolean(PREF_WIFI_OFF, false)) setWifi(ctx, true);
        if (sp.getBoolean(PREF_BT_OFF, false)) setBluetooth(true);
        sp.edit().putBoolean(PREF_SAVING, false).putBoolean(PREF_WIFI_OFF, false).putBoolean(PREF_BT_OFF, false).commit();
        TripService.sync(ctx);
        ObdService.sync(ctx);
        CastService.sync(ctx);
    }

    static boolean isSaving(Context ctx) {
        return sp(ctx).getBoolean(PREF_SAVING, false);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        if (plugged(ctx) || minutes(ctx) <= 0) return;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        @SuppressWarnings("deprecation")
        boolean screenOn = pm != null && (Build.VERSION.SDK_INT >= 20 ? pm.isInteractive() : pm.isScreenOn());
        if (screenOn) {
            arm(ctx); // in use on battery; look again later
            return;
        }
        save(ctx);
    }

    /** Turns things off now and remembers what to turn back on. */
    static void save(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean wifiOn = wm != null && wm.isWifiEnabled();
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        boolean btOn = false;
        try { btOn = bt != null && bt.isEnabled(); } catch (SecurityException ignored) { }

        sp(ctx).edit().putBoolean(PREF_SAVING, true).putBoolean(PREF_WIFI_OFF, wifiOn).putBoolean(PREF_BT_OFF, btOn).commit();
        ctx.stopService(new Intent(ctx, TripService.class));
        ctx.stopService(new Intent(ctx, ObdService.class));
        ctx.stopService(new Intent(ctx, CastService.class));
        AlertOverlay.get(ctx).dismiss();
        if (wifiOn) setWifi(ctx, false);
        if (btOn) setBluetooth(false);
    }

    @SuppressWarnings("deprecation") // Allowed for apps up to Android 9; this tablet runs 5.1.
    private static void setWifi(Context ctx, boolean on) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try { if (wm != null) wm.setWifiEnabled(on); } catch (RuntimeException ignored) { }
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private static void setBluetooth(boolean on) {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        try {
            if (bt == null) return;
            if (on) bt.enable();
            else bt.disable();
        } catch (RuntimeException ignored) { }
    }

    static int minutes(Context ctx) {
        try {
            return Integer.parseInt(sp(ctx).getString(PREF_MINUTES, "10"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean plugged(Context ctx) {
        Intent b = ctx.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private static PendingIntent pending(Context ctx) {
        Intent i = new Intent(ctx, BatterySaver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(ctx, 0, i, flags);
    }

    private static SharedPreferences sp(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }
}

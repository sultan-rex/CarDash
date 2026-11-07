package com.sulthan.cardash;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;

/**
 * Records drives in the background, so distance and top speed keep counting while Android Auto is
 * on screen. Once a second it takes the speed from the OBD-II adapter when that is connected,
 * otherwise from GPS, and adds it to [DriveLog]. The same readings drive the warnings ([Alerts]).
 */
public class TripService extends Service implements LocationListener {
    static final String PREF_ENABLED = "trip_tracking";
    private static final long TICK_MS = 1000;
    /** A GPS speed older than this is treated as lost. */
    private static final long GPS_FRESH_MS = 3000;
    /** Worse than this many metres, a GPS speed is not trusted. */
    private static final float GPS_MAX_ACCURACY_M = 25f;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private DriveLog log;
    private Alerts alerts;
    private SharedPreferences sp;
    private LocationManager lm;
    private long lastTick;
    private double gpsKmh = Double.NaN;
    private long gpsAt;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            double dt = lastTick == 0 ? 0 : (now - lastTick) / 1000.0;
            lastTick = now;
            double speed = currentSpeed(now);
            if (sp.getBoolean(PREF_ENABLED, true)) log.add(System.currentTimeMillis(), speed, dt);
            alerts.tick(speed);
            ui.postDelayed(this, TICK_MS);
        }
    };

    /**
     * Starts or stops the service to match the settings. It runs while drives are recorded or any
     * warning is on, and pauses while the battery saver has the car switched off.
     */
    static void sync(Context ctx) {
        Intent i = new Intent(ctx, TripService.class);
        if (wanted(ctx)) ctx.startService(i);
        else ctx.stopService(i);
    }

    private static boolean wanted(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        return (sp.getBoolean(PREF_ENABLED, true) || Alerts.anyEnabled(sp)) && !BatterySaver.isSaving(ctx);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        log = DriveLog.get(this);
        alerts = new Alerts(this);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!wanted(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            lm.removeUpdates(this);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        } catch (RuntimeException ignored) {
            // No GPS or no permission: the OBD-II speed alone can still be recorded.
        }
        lastTick = 0;
        ui.removeCallbacks(tick);
        ui.post(tick);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacks(tick);
        try { lm.removeUpdates(this); } catch (RuntimeException ignored) { }
        log.flush();
        super.onDestroy();
    }

    private double currentSpeed(long now) {
        ObdManager obd = ObdManager.get();
        if (obd.state() == ObdManager.State.CONNECTED) {
            double v = obd.snapshot().speedKmh;
            if (!Double.isNaN(v)) return v;
        }
        return now - gpsAt <= GPS_FRESH_MS ? gpsKmh : Double.NaN;
    }

    @Override
    public void onLocationChanged(Location l) {
        boolean good = l.hasSpeed() && (!l.hasAccuracy() || l.getAccuracy() <= GPS_MAX_ACCURACY_M);
        gpsKmh = good ? l.getSpeed() * 3.6 : Double.NaN;
        gpsAt = SystemClock.elapsedRealtime();
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { gpsKmh = Double.NaN; }
}

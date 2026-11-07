package com.sulthan.cardash;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Windshield mode: the speed, very large and mirrored, on black, so the tablet lying flat under the
 * windshield reflects a readable number at night. Tap to change the mirroring; press back (or
 * long-press) to leave.
 */
public class HudActivity extends Activity implements ObdManager.Listener, LocationListener {
    private static final String PREF_MIRROR = "hud_mirror";
    private static final long GPS_FRESH_MS = 3000;

    private Prefs prefs;
    private LinearLayout box;
    private TextView speed, unit, rpm;
    /** 0 = flipped top to bottom, 1 = flipped left to right, 2 = normal. */
    private int mirror;
    private ObdData obd = new ObdData();
    private boolean obdConnected;
    private double gpsKmh = Double.NaN;
    private long gpsAt;
    private LocationManager lm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            ui.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = new Prefs(this);
        mirror = android.preference.PreferenceManager.getDefaultSharedPreferences(this).getInt(PREF_MIRROR, 0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);

        speed = text(thin, 260, 0xFF2EC4B6);
        unit = text(thin, 34, 0xFF7C7C7C);
        rpm = text(thin, 34, 0xFF7C7C7C);
        box.addView(speed);
        box.addView(unit);
        box.addView(rpm);
        root.addView(box, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        root.setOnClickListener(v -> {
            mirror = (mirror + 1) % 3;
            android.preference.PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(PREF_MIRROR, mirror).apply();
            applyMirror();
            Toast.makeText(this, mirror == 0 ? "Flipped top to bottom" : mirror == 1 ? "Flipped left to right" : "Normal",
                    Toast.LENGTH_SHORT).show();
        });
        root.setOnLongClickListener(v -> { finish(); return true; });
        applyMirror();
        Toast.makeText(this, "Tap to change the mirroring. Press back or hold to leave.", Toast.LENGTH_SHORT).show();
    }

    private TextView text(Typeface tf, int sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    /**
     * The glass reverses the image along the line from tablet to windshield. With the tablet's top
     * edge toward the windshield that is top to bottom; with its top edge toward the driver it reads
     * as left to right. Tapping cycles through both and normal.
     */
    private void applyMirror() {
        box.setScaleY(mirror == 0 ? -1f : 1f);
        box.setScaleX(mirror == 1 ? -1f : 1f);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObdManager.get().addListener(this);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
        } catch (RuntimeException ignored) { }
        ui.post(refresh);
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(refresh);
        ObdManager.get().removeListener(this);
        try { lm.removeUpdates(this); } catch (RuntimeException ignored) { }
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) MainActivity.hideSystemBars(this);
    }

    private void render() {
        double kmh = obdConnected && !Double.isNaN(obd.speedKmh) ? obd.speedKmh
                : SystemClock.elapsedRealtime() - gpsAt <= GPS_FRESH_MS ? gpsKmh : Double.NaN;
        speed.setText(Double.isNaN(kmh) ? "--" : String.format(Locale.US, "%.0f", prefs.speed(kmh)));
        unit.setText(prefs.speedUnit());
        rpm.setText(obdConnected && !Double.isNaN(obd.rpm) ? String.format(Locale.US, "%.0f rpm", obd.rpm) : "");
    }

    @Override public void onObdData(ObdData data) { obd = data; }
    @Override public void onObdState(ObdManager.State state, String message) { obdConnected = state == ObdManager.State.CONNECTED; }

    @Override
    public void onLocationChanged(Location l) {
        gpsKmh = l.hasSpeed() ? l.getSpeed() * 3.6 : Double.NaN;
        gpsAt = SystemClock.elapsedRealtime();
    }

    @Override public void onStatusChanged(String p, int s, Bundle b) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { gpsKmh = Double.NaN; }
}

package com.sulthan.cardash;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.List;
import java.util.Locale;

/**
 * An estimate of the car's odometer: the last reading the user typed in (at a fill-up or a
 * service) plus the distance recorded on the days since. Distance driven later that same day is
 * not counted, so the estimate can run a little behind; each new reading corrects it.
 */
final class Odometer {
    private static final String PREF = "odo_anchor"; // "km;timeMs"

    private Odometer() {}

    /** Estimated odometer in km, or NaN when no reading has ever been entered. */
    static double estimateKm(Context ctx) {
        double[] a = anchor(ctx);
        if (Double.isNaN(a[0])) return Double.NaN;
        return a[0] + DriveLog.get(ctx).kmAfter(DriveLog.key((long) a[1]));
    }

    /** Remembers a reading the user typed in. */
    static void record(Context ctx, double km, long timeMs) {
        prefs(ctx).edit().putString(PREF, String.format(Locale.US, "%.1f;%d", km, timeMs)).apply();
    }

    /** The newest of the stored reading and the last fill-up: {km, timeMs}. */
    private static double[] anchor(Context ctx) {
        double km = Double.NaN;
        long time = 0;
        String s = prefs(ctx).getString(PREF, null);
        if (s != null) {
            String[] p = s.split(";");
            try {
                km = Double.parseDouble(p[0]);
                time = Long.parseLong(p[1]);
            } catch (RuntimeException ignored) { }
        }
        List<FuelLog.Entry> fills = new FuelLog(ctx).entries(); // newest first
        if (!fills.isEmpty()) {
            FuelLog.Entry f = fills.get(0);
            if (Double.isNaN(km) || f.time > time) {
                km = f.odoKm;
                time = f.time;
            }
        }
        return new double[]{km, time};
    }

    private static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }
}

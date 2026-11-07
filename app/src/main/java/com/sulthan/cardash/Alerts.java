package com.sulthan.cardash;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Locale;

/** Connects [AlertRules] to the settings, the live readings and the warning banner. */
final class Alerts implements AlertRules.Sink {
    static final String PREF_COOLANT = "alert_coolant";
    static final String PREF_BATTERY = "alert_battery";
    static final String PREF_SPEED = "alert_speed";

    private final Context ctx;
    private final SharedPreferences sp;
    private final Prefs prefs;
    private final AlertRules rules = new AlertRules();

    Alerts(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        sp = PreferenceManager.getDefaultSharedPreferences(this.ctx);
        prefs = new Prefs(this.ctx);
    }

    /** True when any warning is switched on, so the background service has a reason to run. */
    static boolean anyEnabled(SharedPreferences sp) {
        AlertRules.Limits l = limits(sp);
        return l.battery || !Double.isNaN(l.coolantC) || !Double.isNaN(l.speedKmh);
    }

    static AlertRules.Limits limits(SharedPreferences sp) {
        AlertRules.Limits l = new AlertRules.Limits();
        l.coolantC = number(sp.getString(PREF_COOLANT, "110"));
        l.battery = sp.getBoolean(PREF_BATTERY, true);
        double speed = number(sp.getString(PREF_SPEED, ""));
        boolean mph = "mph".equals(sp.getString("speed_unit", "kmh"));
        // The limit is typed in the speed unit shown on screen; the rules work in km/h.
        l.speedKmh = speed > 0 ? (mph ? speed / 0.621371 : speed) : Double.NaN;
        return l;
    }

    private static double number(String s) {
        try {
            return s == null ? Double.NaN : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** One reading, about once a second. Engine values come from the adapter when it is connected. */
    void tick(double speedKmh) {
        ObdData d = ObdManager.get().state() == ObdManager.State.CONNECTED ? ObdManager.get().snapshot() : new ObdData();
        rules.update(System.currentTimeMillis(), speedKmh, d.coolantC, d.voltage, d.rpm, limits(sp), this);
    }

    @Override
    public void raise(AlertRules.Kind kind, double v) {
        AlertOverlay o = AlertOverlay.get(ctx);
        switch (kind) {
            case COOLANT:
                o.show(kind.name(), "Engine too hot",
                        String.format(Locale.US, "Coolant %.0f%s. Pull over safely and let the engine idle or switch it off.",
                                prefs.temp(v), prefs.tempUnit()), AlertOverlay.RED, true, 0);
                break;
            case LOW_BATTERY:
                o.show(kind.name(), "Car battery low",
                        String.format(Locale.US, "%.1f V with the engine off. It may not start if this keeps dropping.", v),
                        AlertOverlay.AMBER, true, 0);
                break;
            case NOT_CHARGING:
                o.show(kind.name(), "Battery not charging",
                        String.format(Locale.US, "%.1f V with the engine running (normal is 13.5 to 14.7 V). Check the alternator or belt.", v),
                        AlertOverlay.AMBER, true, 0);
                break;
            case SPEED:
                o.show(kind.name(), String.format(Locale.US, "%.0f %s", prefs.speed(v), prefs.speedUnit()),
                        "Over your speed alert", AlertOverlay.AMBER, true, 6000);
                break;
        }
    }

    @Override
    public void clear(AlertRules.Kind kind) {
        AlertOverlay.get(ctx).dismiss(kind.name());
    }
}

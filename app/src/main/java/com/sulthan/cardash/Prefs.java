package com.sulthan.cardash;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/** Typed access to the settings screen values, plus unit conversion. */
public final class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    public boolean obdEnabled() { return sp.getBoolean("obd_enabled", false); }
    public String obdDevice() { return sp.getString("obd_device", null); }
    public boolean mph() { return "mph".equals(sp.getString("speed_unit", "kmh")); }
    public boolean fahrenheit() { return "f".equals(sp.getString("temp_unit", "c")); }
    public boolean aaAutoLaunch() { return sp.getBoolean("aa_autolaunch", false); }
    public String aaSsid() { return sp.getString("aa_ssid", "").trim(); }
    public String navApp() { return sp.getString("nav_app", "auto"); }
    public boolean launchOnPower() { return sp.getBoolean("launch_on_power", true); }

    /** True when the OBD service should be running (an adapter is enabled and chosen). */
    public boolean wantsObd() {
        return obdEnabled() && obdDevice() != null;
    }

    public double speed(double kmh) { return mph() ? kmh * 0.621371 : kmh; }
    public String speedUnit() { return mph() ? "mph" : "km/h"; }
    public double temp(double c) { return fahrenheit() ? c * 9 / 5 + 32 : c; }
    public String tempUnit() { return fahrenheit() ? "°F" : "°C"; }
}

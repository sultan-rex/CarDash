package com.sulthan.cardash;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;

import java.util.Calendar;

/**
 * Day and night look. The choice is Automatic (night from 7 pm until 6 am), Day, or Night. It is applied by
 * giving each activity a context whose "night" flag is set to match, so the values-night resources (the dark
 * palette and theme) are used at night and the plain values (the light ones) by day.
 */
final class ThemeMode {
    static final String PREF = "theme_mode";
    static final String AUTO = "auto", DAY = "day", NIGHT = "night";
    /** Automatic mode: night starts at this hour (24-hour clock)... */
    static final int NIGHT_FROM_HOUR = 19;
    /** ...and day starts again at this hour. */
    static final int DAY_FROM_HOUR = 6;

    private ThemeMode() { }

    /** Whether it should be night for the chosen [mode] at [hour] (0-23). */
    static boolean isNight(String mode, int hour) {
        if (NIGHT.equals(mode)) return true;
        if (DAY.equals(mode)) return false;
        return hour >= NIGHT_FROM_HOUR || hour < DAY_FROM_HOUR;
    }

    static String mode(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sp.getString(PREF, AUTO);
    }

    static void setMode(Context ctx, String mode) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(PREF, mode).apply();
    }

    static boolean night(Context ctx) {
        return isNight(mode(ctx), Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    /** For an activity's attachBaseContext: a context that uses the day or night resources. */
    static Context wrap(Context base) {
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        conf.uiMode = (conf.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night(base) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(conf);
    }

    /** Dialog theme to match: dark at night, light by day. */
    static int dialogTheme(Context ctx) {
        return night(ctx) ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
    }
}

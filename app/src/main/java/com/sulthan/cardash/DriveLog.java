package com.sulthan.cardash;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-day drive history: distance, top speed and time spent moving. Speeds are fed in about once
 * a second; distance is speed times the time since the last sample. Stored as one text line per
 * day: "yyyy-MM-dd;km;topKmh;movingSeconds".
 */
final class DriveLog {
    /** Where the text is kept; the app uses SharedPreferences, tests use memory. */
    interface Store {
        String read();
        void write(String text);
    }

    static final class Day {
        final String date;
        double km;
        double topKmh;
        double movingSec;

        Day(String date) { this.date = date; }

        double avgKmh() { return movingSec > 0 ? km / (movingSec / 3600.0) : 0; }
    }

    /**
     * Below this the car counts as stopped. GPS speed wobbles by 3-4 km/h while standing still, so
     * anything lower would record phantom drives.
     */
    static final double MOVING_KMH = 5.0;
    /** Faster than this is a bad reading, not a speed. */
    static final double MAX_KMH = 300.0;
    /** A gap longer than this between samples is not driven distance (the app was not running). */
    static final double MAX_GAP_SEC = 10.0;
    private static final int KEEP_DAYS = 800;
    private static final long SAVE_EVERY_MS = 30_000;

    private static DriveLog instance;

    private final Store store;
    private final Map<String, Day> days = new TreeMap<>();
    private boolean dirty;
    private long lastSave;

    static synchronized DriveLog get(Context ctx) {
        if (instance == null) {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx.getApplicationContext());
            instance = new DriveLog(new Store() {
                @Override public String read() { return sp.getString("drive_log", ""); }
                @Override public void write(String text) { sp.edit().putString("drive_log", text).apply(); }
            });
        }
        return instance;
    }

    DriveLog(Store store) {
        this.store = store;
        load();
    }

    static String key(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
    }

    static String key(int year, int month0, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month0 + 1, day);
    }

    /** One speed reading. [dtSec] is the time since the previous reading. */
    synchronized void add(long nowMs, double speedKmh, double dtSec) {
        if (Double.isNaN(speedKmh) || speedKmh < 0 || speedKmh > MAX_KMH) return;
        String k = key(nowMs);
        Day d = days.get(k);
        boolean moving = speedKmh >= MOVING_KMH;
        if (d == null) {
            if (!moving) return; // do not create empty days
            d = new Day(k);
            days.put(k, d);
        }
        if (speedKmh > d.topKmh && moving) {
            d.topKmh = speedKmh;
            dirty = true;
        }
        if (moving && dtSec > 0 && dtSec <= MAX_GAP_SEC) {
            d.km += speedKmh * dtSec / 3600.0;
            d.movingSec += dtSec;
            dirty = true;
        }
        saveIfDue(nowMs);
    }

    synchronized Day day(String key) { return days.get(key); }

    synchronized Day today(long nowMs) { return days.get(key(nowMs)); }

    synchronized double monthTop(int year, int month0) {
        double top = 0;
        for (Day d : days.values()) if (inMonth(d, year, month0) && d.topKmh > top) top = d.topKmh;
        return top;
    }

    synchronized double monthKm(int year, int month0) {
        double km = 0;
        for (Day d : days.values()) if (inMonth(d, year, month0)) km += d.km;
        return km;
    }

    synchronized int monthDays(int year, int month0) {
        int n = 0;
        for (Day d : days.values()) if (inMonth(d, year, month0) && d.km > 0) n++;
        return n;
    }

    private static boolean inMonth(Day d, int year, int month0) {
        return d.date.startsWith(String.format(Locale.US, "%04d-%02d-", year, month0 + 1));
    }

    synchronized boolean isEmpty() { return days.isEmpty(); }

    synchronized void clear() {
        days.clear();
        dirty = true;
        flush();
    }

    private void saveIfDue(long nowMs) {
        if (dirty && nowMs - lastSave >= SAVE_EVERY_MS) flushAt(nowMs);
    }

    synchronized void flush() { flushAt(System.currentTimeMillis()); }

    private void flushAt(long nowMs) {
        while (days.size() > KEEP_DAYS) days.remove(days.keySet().iterator().next());
        StringBuilder sb = new StringBuilder();
        for (Day d : days.values()) {
            sb.append(d.date).append(';').append(String.format(Locale.US, "%.4f;%.1f;%.0f", d.km, d.topKmh, d.movingSec))
                    .append('\n');
        }
        store.write(sb.toString());
        dirty = false;
        lastSave = nowMs;
    }

    private void load() {
        days.clear();
        String text = store.read();
        if (text == null) return;
        for (String line : text.split("\n")) {
            String[] p = line.split(";");
            if (p.length != 4) continue;
            try {
                Day d = new Day(p[0]);
                d.km = Double.parseDouble(p[1]);
                d.topKmh = Double.parseDouble(p[2]);
                d.movingSec = Double.parseDouble(p[3]);
                days.put(d.date, d);
            } catch (NumberFormatException ignored) { }
        }
    }
}

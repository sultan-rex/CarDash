package com.sulthan.cardash;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Saves and restores every CarDash and Android Auto setting, the fuel log, drive history and
 * service reminders as one file, and writes the drives and fill-ups as spreadsheet (CSV) files.
 * Everything goes to the CarDash folder on the tablet's storage, where the Mac can copy it over USB
 * (a factory reset wipes that folder too, so copy the backup off the tablet).
 */
final class Backup {
    static final String FOLDER = "CarDash";
    static final String BACKUP_FILE = "cardash-backup.json";
    private static final int FORMAT = 1;

    private Backup() {}

    static File folder() {
        return new File(Environment.getExternalStorageDirectory(), FOLDER);
    }

    /** CarDash's own settings and Open Headunit's (Android Auto) settings. */
    private static String[] prefFiles(Context ctx) {
        return new String[]{ctx.getPackageName() + "_preferences", "settings"};
    }

    /** Writes the backup file and returns it. */
    static File export(Context ctx) throws IOException, JSONException {
        DriveLog.get(ctx).flush();
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("saved", System.currentTimeMillis());
        JSONObject files = new JSONObject();
        for (String name : prefFiles(ctx)) {
            SharedPreferences sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                if (transientKey(e.getKey())) continue;
                Object v = e.getValue();
                JSONObject typed = new JSONObject();
                if (v instanceof Boolean) typed.put("b", v);
                else if (v instanceof Integer) typed.put("i", v);
                else if (v instanceof Long) typed.put("l", v);
                else if (v instanceof Float) typed.put("f", ((Float) v).doubleValue());
                else if (v instanceof String) typed.put("s", v);
                else if (v instanceof Set) typed.put("set", new JSONArray((Set<?>) v));
                else continue;
                values.put(e.getKey(), typed);
            }
            files.put(name, values);
        }
        root.put("prefs", files);
        File out = new File(folder(), BACKUP_FILE);
        write(out, root.toString(1));
        return out;
    }

    /**
     * Restores a backup file over the current settings. Returns how many values were restored.
     * The caller restarts the app afterwards so every part re-reads them.
     */
    static int restore(Context ctx, File in) throws IOException, JSONException {
        JSONObject root = new JSONObject(read(in));
        JSONObject files = root.getJSONObject("prefs");
        int n = 0;
        for (String name : prefFiles(ctx)) {
            JSONObject values = files.optJSONObject(name);
            if (values == null) continue;
            SharedPreferences.Editor ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            JSONArray keys = values.names();
            for (int i = 0; keys != null && i < keys.length(); i++) {
                String key = keys.getString(i);
                JSONObject t = values.getJSONObject(key);
                if (t.has("b")) ed.putBoolean(key, t.getBoolean("b"));
                else if (t.has("i")) ed.putInt(key, t.getInt("i"));
                else if (t.has("l")) ed.putLong(key, t.getLong("l"));
                else if (t.has("f")) ed.putFloat(key, (float) t.getDouble("f"));
                else if (t.has("s")) ed.putString(key, t.getString("s"));
                else if (t.has("set")) {
                    JSONArray a = t.getJSONArray("set");
                    Set<String> s = new HashSet<>();
                    for (int k = 0; k < a.length(); k++) s.add(a.getString(k));
                    ed.putStringSet(key, s);
                } else continue;
                n++;
            }
            if (!ed.commit()) throw new IOException("Could not save " + name);
        }
        return n;
    }

    /** Short-lived state that must not travel to another day or tablet. */
    private static boolean transientKey(String k) {
        return k.startsWith("saver_") || k.equals("crash_times") || k.equals("service_reminded_day");
    }

    /** Writes drives.csv and fuel.csv; returns the folder. */
    static File exportCsv(Context ctx) throws IOException {
        DriveLog.get(ctx).flush();
        StringBuilder d = new StringBuilder("date,km,top_kmh,moving_minutes,avg_kmh\n");
        for (DriveLog.Day day : DriveLog.get(ctx).allDays()) {
            d.append(String.format(Locale.US, "%s,%.2f,%.0f,%.0f,%.1f\n",
                    day.date, day.km, day.topKmh, day.movingSec / 60.0, day.avgKmh()));
        }
        write(new File(folder(), "drives.csv"), d.toString());

        StringBuilder f = new StringBuilder("date,odometer_km,litres,cost,full_tank,l_per_100km\n");
        List<FuelLog.Entry> fills = new FuelLog(ctx).entries();
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        for (int i = fills.size() - 1; i >= 0; i--) { // oldest first
            FuelLog.Entry e = fills.get(i);
            f.append(String.format(Locale.US, "%s,%.0f,%.2f,%.2f,%s,%s\n", iso.format(new Date(e.time)), e.odoKm,
                    e.litres, e.cost, e.full ? "yes" : "no", Double.isNaN(e.l100) ? "" : String.format(Locale.US, "%.2f", e.l100)));
        }
        write(new File(folder(), "fuel.csv"), f.toString());
        return folder();
    }

    static void write(File out, String text) throws IOException {
        File dir = out.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File tmp = new File(out.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(text);
        }
        if (!tmp.renameTo(out)) throw new IOException("Cannot write " + out);
    }

    private static String read(File in) throws IOException {
        try (InputStream is = new FileInputStream(in)) {
            byte[] buf = new byte[(int) Math.min(in.length(), 4 << 20)];
            int n = 0, r;
            while (n < buf.length && (r = is.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }
}

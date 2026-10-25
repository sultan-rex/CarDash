package com.sulthan.cardash;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** The fill-up history and the numbers worked out from it. Stored in km, litres and money. */
public final class FuelLog {
    public static final class Entry {
        public long time;
        public double odoKm;
        public double litres;
        public double cost;
        public boolean full;
        /** Litres per 100 km for the full-to-full stretch ending at this fill-up, else NaN. */
        public double l100 = Double.NaN;
    }

    public static final class Stats {
        public int fillUps;
        public double avgL100 = Double.NaN;
        public double lastL100 = Double.NaN;
        public double bestL100 = Double.NaN;
        public double worstL100 = Double.NaN;
        public double totalKm;
        public double totalLitres;
        public double totalCost;
        public double costPerKm = Double.NaN;
        public double avgPricePerLitre = Double.NaN;
        public double avgKmPerFill = Double.NaN;
        public double monthCost;
    }

    private static final String KEY = "fuel_log";
    private final SharedPreferences sp;
    private final List<Entry> entries = new ArrayList<>();

    public FuelLog(Context ctx) {
        sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        load();
        recompute();
    }

    /** Newest first. */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>(entries);
        Collections.reverse(out);
        return out;
    }

    public double lastOdometerKm() {
        return entries.isEmpty() ? Double.NaN : entries.get(entries.size() - 1).odoKm;
    }

    public void add(Entry e) {
        entries.add(e);
        recompute();
        save();
    }

    public void remove(Entry e) {
        entries.remove(e);
        recompute();
        save();
    }

    private void load() {
        entries.clear();
        try {
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.time = o.getLong("t");
                e.odoKm = o.getDouble("odo");
                e.litres = o.getDouble("l");
                e.cost = o.getDouble("c");
                e.full = o.getBoolean("f");
                entries.add(e);
            }
        } catch (JSONException ignored) {
            entries.clear();
        }
    }

    private void save() {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : entries) {
                a.put(new JSONObject().put("t", e.time).put("odo", e.odoKm)
                        .put("l", e.litres).put("c", e.cost).put("f", e.full));
            }
        } catch (JSONException ignored) { }
        sp.edit().putString(KEY, a.toString()).apply();
    }

    /** Orders by odometer and fills in each entry's full-to-full consumption. */
    private void recompute() {
        Collections.sort(entries, new Comparator<Entry>() {
            @Override public int compare(Entry x, Entry y) { return Double.compare(x.odoKm, y.odoKm); }
        });
        double baseOdo = Double.NaN, acc = 0;
        for (Entry e : entries) {
            e.l100 = Double.NaN;
            if (Double.isNaN(baseOdo)) {
                if (e.full) baseOdo = e.odoKm; // the first full tank only sets the starting point
                continue;
            }
            acc += e.litres;
            if (e.full) {
                double km = e.odoKm - baseOdo;
                if (km > 0) e.l100 = acc / km * 100;
                baseOdo = e.odoKm;
                acc = 0;
            }
        }
    }

    public Stats stats() {
        Stats s = new Stats();
        s.fillUps = entries.size();
        if (entries.isEmpty()) return s;

        double segFuel = 0, segKm = 0;
        double best = Double.NaN, worst = Double.NaN;
        int segments = 0;
        double fillKm = 0;
        for (Entry e : entries) {
            s.totalLitres += e.litres;
            s.totalCost += e.cost;
            if (!Double.isNaN(e.l100)) {
                segments++;
                if (Double.isNaN(best) || e.l100 < best) best = e.l100;
                if (Double.isNaN(worst) || e.l100 > worst) worst = e.l100;
                s.lastL100 = e.l100;
            }
        }
        // Weighted average over the full-to-full stretches, so long drives count for more.
        double baseOdo = Double.NaN, acc = 0;
        for (Entry e : entries) {
            if (Double.isNaN(baseOdo)) { if (e.full) baseOdo = e.odoKm; continue; }
            acc += e.litres;
            if (e.full) {
                double km = e.odoKm - baseOdo;
                if (km > 0) { segFuel += acc; segKm += km; fillKm += km; }
                baseOdo = e.odoKm;
                acc = 0;
            }
        }
        if (segKm > 0) s.avgL100 = segFuel / segKm * 100;
        s.bestL100 = best;
        s.worstL100 = worst;
        if (segments > 0) s.avgKmPerFill = fillKm / segments;

        Entry first = entries.get(0), last = entries.get(entries.size() - 1);
        s.totalKm = last.odoKm - first.odoKm;
        if (s.totalKm > 0) {
            // Fuel bought after the first entry is what the distance since then used.
            double cost = 0;
            for (int i = 1; i < entries.size(); i++) cost += entries.get(i).cost;
            s.costPerKm = cost / s.totalKm;
        }
        if (s.totalLitres > 0) s.avgPricePerLitre = s.totalCost / s.totalLitres;

        Calendar now = Calendar.getInstance(), c = Calendar.getInstance();
        for (Entry e : entries) {
            c.setTimeInMillis(e.time);
            if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                    && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)) s.monthCost += e.cost;
        }
        return s;
    }
}

package com.sulthan.cardash;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Service reminders: each job (oil change, tyre rotation…) repeats every so many km and/or months,
 * counted from when it was last done. Stored as one text line per job:
 * "name|everyKm|everyMonths|doneOdoKm|doneTimeMs" (doneOdoKm is "-" until the job is first recorded).
 */
final class ServiceLog {
    interface Store {
        String read();
        void write(String text);
    }

    static final class Item {
        String name;
        int everyKm;      // 0 = no distance interval
        int everyMonths;  // 0 = no time interval
        double doneOdoKm = Double.NaN;
        long doneTime;    // 0 = never recorded

        Item(String name, int everyKm, int everyMonths) {
            this.name = name;
            this.everyKm = everyKm;
            this.everyMonths = everyMonths;
        }

        boolean recorded() { return doneTime > 0; }
    }

    static final class Status {
        boolean unset, due, soon;
        double kmLeft = Double.NaN;
        long daysLeft = Long.MAX_VALUE;
    }

    /** "Soon" starts this far before the job is due. */
    static final double SOON_KM = 500;
    static final long SOON_DAYS = 30;
    private static final long DAY_MS = 86_400_000L;

    private final Store store;
    private final List<Item> items = new ArrayList<>();

    ServiceLog(Store store) {
        this.store = store;
        load();
    }

    List<Item> items() { return items; }

    void add(Item it) { items.add(it); save(); }

    void remove(Item it) { items.remove(it); save(); }

    void markDone(Item it, double odoKm, long time) {
        it.doneOdoKm = odoKm;
        it.doneTime = time;
        save();
    }

    static Status status(Item it, double odoNowKm, long nowMs) {
        Status s = new Status();
        if (!it.recorded()) {
            s.unset = true;
            return s;
        }
        if (it.everyKm > 0 && !Double.isNaN(it.doneOdoKm) && !Double.isNaN(odoNowKm)) {
            s.kmLeft = it.doneOdoKm + it.everyKm - odoNowKm;
        }
        if (it.everyMonths > 0) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(it.doneTime);
            c.add(Calendar.MONTH, it.everyMonths);
            long ms = c.getTimeInMillis() - nowMs;
            // Whole days still to go, counting a part day as a day; 0 or less once the date has passed.
            s.daysLeft = ms > 0 ? (ms + DAY_MS - 1) / DAY_MS : -((-ms) / DAY_MS);
        }
        s.due = (!Double.isNaN(s.kmLeft) && s.kmLeft <= 0) || s.daysLeft <= 0;
        s.soon = !s.due && ((!Double.isNaN(s.kmLeft) && s.kmLeft <= SOON_KM) || s.daysLeft <= SOON_DAYS);
        return s;
    }

    /** A short line such as "Due now", "2,300 km left" or "in 3 months". */
    static String describe(Status s, boolean miles) {
        if (s.unset) return "Tap to record when it was last done";
        if (s.due) return "Due now";
        List<String> parts = new ArrayList<>();
        if (!Double.isNaN(s.kmLeft)) {
            double v = miles ? s.kmLeft * 0.621371 : s.kmLeft;
            parts.add(String.format(Locale.US, "%,d %s", Math.round(v), miles ? "mi" : "km"));
        }
        if (s.daysLeft != Long.MAX_VALUE) {
            parts.add(s.daysLeft >= 60 ? (s.daysLeft / 30) + " months" : s.daysLeft + " days");
        }
        if (parts.isEmpty()) return "No interval set";
        return (parts.size() == 1 ? parts.get(0) : parts.get(0) + " or " + parts.get(1)) + " left";
    }

    private void load() {
        items.clear();
        String text = store.read();
        if (text == null || text.trim().isEmpty()) {
            defaults();
            return;
        }
        for (String line : text.split("\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length != 5) continue;
            try {
                Item it = new Item(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                it.doneOdoKm = p[3].equals("-") ? Double.NaN : Double.parseDouble(p[3]);
                it.doneTime = Long.parseLong(p[4]);
                items.add(it);
            } catch (NumberFormatException ignored) { }
        }
    }

    private void defaults() {
        items.add(new Item("Engine oil and filter", 10_000, 12));
        items.add(new Item("Tyre rotation", 10_000, 0));
        items.add(new Item("Air filter", 20_000, 24));
        items.add(new Item("Brake fluid", 0, 24));
    }

    void save() {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            sb.append(clean(it.name)).append('|').append(it.everyKm).append('|').append(it.everyMonths).append('|')
                    .append(Double.isNaN(it.doneOdoKm) ? "-" : String.format(Locale.US, "%.0f", it.doneOdoKm))
                    .append('|').append(it.doneTime).append('\n');
        }
        store.write(sb.toString());
    }

    static String clean(String name) {
        String n = name == null ? "" : name.replace('|', '/').replace('\n', ' ').trim();
        return n.isEmpty() ? "Service" : n;
    }
}

package com.sulthan.cardash;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** The service reminder screens (dialogs) and the once-a-day "service due" banner. */
final class ServiceUi {
    private static final String PREF_ITEMS = "service_items";
    private static final String PREF_REMINDED = "service_reminded_day";

    private ServiceUi() {}

    static ServiceLog log(Context ctx) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        return new ServiceLog(new ServiceLog.Store() {
            @Override public String read() { return sp.getString(PREF_ITEMS, ""); }
            @Override public void write(String text) { sp.edit().putString(PREF_ITEMS, text).apply(); }
        });
    }

    /** Short status for the Gauges page: the most urgent job, or "All up to date". */
    static String summary(Context ctx) {
        ServiceLog log = log(ctx);
        double odo = Odometer.estimateKm(ctx);
        long now = System.currentTimeMillis();
        String soon = null;
        boolean anySet = false;
        for (ServiceLog.Item it : log.items()) {
            ServiceLog.Status s = ServiceLog.status(it, odo, now);
            if (s.unset) continue;
            anySet = true;
            if (s.due) return it.name + " due";
            if (s.soon && soon == null) soon = it.name + " soon";
        }
        if (soon != null) return soon;
        return anySet ? "All up to date" : "Not set up";
    }

    /** Once a day, when CarDash comes up, a banner for any job that is due. */
    static void remindIfDue(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String today = DriveLog.key(System.currentTimeMillis());
        if (today.equals(sp.getString(PREF_REMINDED, ""))) return;
        double odo = Odometer.estimateKm(ctx);
        StringBuilder due = new StringBuilder();
        for (ServiceLog.Item it : log(ctx).items()) {
            if (ServiceLog.status(it, odo, System.currentTimeMillis()).due) {
                if (due.length() > 0) due.append(", ");
                due.append(it.name);
            }
        }
        if (due.length() == 0) return;
        sp.edit().putString(PREF_REMINDED, today).apply();
        AlertOverlay.get(ctx).show("SERVICE", "Service due", due + ". Open Gauges, then Service, to mark it done.",
                AlertOverlay.AMBER, false, 12_000);
    }

    // ------------------------------------------------------------------ dialogs

    static void show(Activity act) {
        Prefs prefs = new Prefs(act);
        ServiceLog log = log(act);
        double odo = Odometer.estimateKm(act);
        List<ServiceLog.Item> items = log.items();
        CharSequence[] rows = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ServiceLog.Item it = items.get(i);
            ServiceLog.Status s = ServiceLog.status(it, odo, System.currentTimeMillis());
            rows[i] = it.name + "   ·   every " + interval(it, prefs) + "\n"
                    + (s.due ? "⚠ " : "") + ServiceLog.describe(s, prefs.mph());
        }
        String title = Double.isNaN(odo) ? "Service"
                : String.format(Locale.US, "Service   ·   odometer about %,d %s", Math.round(prefs.dist(odo)), prefs.distUnit());
        new AlertDialog.Builder(act, ThemeMode.dialogTheme(act))
                .setTitle(title)
                .setItems(rows, (d, which) -> itemMenu(act, log, items.get(which)))
                .setPositiveButton("Close", null)
                .setNeutralButton("Add job", (d, w) -> edit(act, log, null))
                .show();
    }

    private static String interval(ServiceLog.Item it, Prefs prefs) {
        String km = it.everyKm > 0 ? String.format(Locale.US, "%,d %s", Math.round(prefs.dist(it.everyKm)), prefs.distUnit()) : null;
        String mo = it.everyMonths > 0 ? it.everyMonths + (it.everyMonths == 1 ? " month" : " months") : null;
        if (km != null && mo != null) return km + " or " + mo;
        return km != null ? km : mo != null ? mo : "(no interval)";
    }

    private static void itemMenu(Activity act, ServiceLog log, ServiceLog.Item it) {
        new AlertDialog.Builder(act, ThemeMode.dialogTheme(act))
                .setTitle(it.name)
                .setItems(new CharSequence[]{"Mark as done", "Change interval", "Delete"}, (d, which) -> {
                    if (which == 0) markDone(act, log, it);
                    else if (which == 1) edit(act, log, it);
                    else confirmDelete(act, log, it);
                })
                .setNegativeButton("Back", (d, w) -> show(act))
                .show();
    }

    private static void markDone(Activity act, ServiceLog log, ServiceLog.Item it) {
        Prefs prefs = new Prefs(act);
        LinearLayout box = box(act);
        TextView note = new TextView(act);
        note.setText("Odometer reading now (" + prefs.distUnit() + "). It also corrects the odometer estimate.");
        box.addView(note);
        double est = Odometer.estimateKm(act);
        EditText odo = field(act, box, "Odometer", Double.isNaN(est) ? "" : String.valueOf(Math.round(prefs.dist(est))));
        new AlertDialog.Builder(act, ThemeMode.dialogTheme(act))
                .setTitle(it.name + " done today")
                .setView(box)
                .setNegativeButton("Cancel", (d, w) -> show(act))
                .setPositiveButton("Save", (d, w) -> {
                    hideKeyboard(act, odo);
                    double v = num(odo);
                    long now = System.currentTimeMillis();
                    double km = Double.isNaN(v) ? Double.NaN : (prefs.mph() ? v / 0.621371 : v);
                    if (!Double.isNaN(km)) Odometer.record(act, km, now);
                    log.markDone(it, km, now);
                    show(act);
                })
                .show();
    }

    private static void edit(Activity act, ServiceLog log, ServiceLog.Item existing) {
        Prefs prefs = new Prefs(act);
        LinearLayout box = box(act);
        EditText name = field(act, box, "Job (for example Coolant)", existing == null ? "" : existing.name);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText km = field(act, box, "Every how many " + prefs.distUnit() + " (blank = none)",
                existing == null || existing.everyKm == 0 ? "" : String.valueOf(Math.round(prefs.dist(existing.everyKm))));
        EditText months = field(act, box, "Every how many months (blank = none)",
                existing == null || existing.everyMonths == 0 ? "" : String.valueOf(existing.everyMonths));
        new AlertDialog.Builder(act, ThemeMode.dialogTheme(act))
                .setTitle(existing == null ? "Add a service job" : "Change interval")
                .setView(box)
                .setNegativeButton("Cancel", (d, w) -> show(act))
                .setPositiveButton("Save", (d, w) -> {
                    hideKeyboard(act, name);
                    double k = num(km);
                    double m = num(months);
                    int everyKm = Double.isNaN(k) ? 0 : (int) Math.round(prefs.mph() ? k / 0.621371 : k);
                    int everyMonths = Double.isNaN(m) ? 0 : (int) Math.round(m);
                    String n = ServiceLog.clean(name.getText().toString());
                    if (existing == null) {
                        log.add(new ServiceLog.Item(n, everyKm, everyMonths));
                    } else {
                        existing.name = n;
                        existing.everyKm = everyKm;
                        existing.everyMonths = everyMonths;
                        log.save();
                    }
                    show(act);
                })
                .show();
    }

    private static void confirmDelete(Activity act, ServiceLog log, ServiceLog.Item it) {
        new AlertDialog.Builder(act, ThemeMode.dialogTheme(act))
                .setTitle("Delete " + it.name + "?")
                .setNegativeButton("Cancel", (d, w) -> show(act))
                .setPositiveButton("Delete", (d, w) -> {
                    log.remove(it);
                    show(act);
                })
                .show();
    }

    private static LinearLayout box(Activity act) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(20 * act.getResources().getDisplayMetrics().density);
        box.setPadding(p, p / 2, p, 0);
        return box;
    }

    private static EditText field(Activity act, LinearLayout box, String hint, String prefill) {
        EditText f = new EditText(act);
        f.setHint(hint);
        f.setSingleLine(true);
        f.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (prefill != null) f.setText(prefill);
        box.addView(f);
        return f;
    }

    private static void hideKeyboard(Activity act, EditText f) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    private static double num(EditText f) {
        try {
            String s = f.getText().toString().trim().replace(",", "");
            return s.isEmpty() ? Double.NaN : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}

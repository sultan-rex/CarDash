package com.sulthan.cardash;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The fuel page: fill-up list, the numbers worked out from it, and the add-fill-up dialog. */
final class FuelPage {
    private static final double MI_PER_KM = 0.621371;
    private static final double GAL_PER_L = 0.264172;

    private final Activity act;
    private final Prefs prefs;
    private final FuelLog log;
    private final TextView avg, avgUnit, last, best, costPerKm, month, total, range, empty;
    private final ListView list;

    FuelPage(Activity act, Prefs prefs) {
        this.act = act;
        this.prefs = prefs;
        log = new FuelLog(act);
        avg = act.findViewById(R.id.fuel_avg);
        avgUnit = act.findViewById(R.id.fuel_avg_unit);
        last = act.findViewById(R.id.fuel_last);
        best = act.findViewById(R.id.fuel_best);
        costPerKm = act.findViewById(R.id.fuel_cost_km);
        month = act.findViewById(R.id.fuel_month);
        total = act.findViewById(R.id.fuel_total);
        range = act.findViewById(R.id.fuel_range);
        empty = act.findViewById(R.id.fuel_empty);
        list = act.findViewById(R.id.fuel_list);
        list.setEmptyView(empty);
        act.findViewById(R.id.btn_fuel_add).setOnClickListener(v -> showAddDialog());
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            confirmDelete((FuelLog.Entry) p.getItemAtPosition(pos));
            return true;
        });
    }

    // ---------------------------------------------------------------- units

    private boolean imperial() { return prefs.mph(); }
    private String distUnit() { return imperial() ? "mi" : "km"; }
    private String volUnit() { return imperial() ? "gal" : "L"; }
    private double dist(double km) { return imperial() ? km * MI_PER_KM : km; }
    private double vol(double l) { return imperial() ? l * GAL_PER_L : l; }
    private double toKm(double d) { return imperial() ? d / MI_PER_KM : d; }
    private double toLitres(double v) { return imperial() ? v / GAL_PER_L : v; }

    /** Litres per 100 km, or miles per US gallon when the speed unit is mph. */
    private String consumption(double l100) {
        if (Double.isNaN(l100) || l100 <= 0) return "--";
        return String.format(Locale.getDefault(), "%.1f", imperial() ? 235.215 / l100 : l100);
    }

    private String consumptionUnit() { return imperial() ? "mpg" : "L/100 km"; }

    private static String money(double v) {
        return Double.isNaN(v) ? "--" : NumberFormat.getCurrencyInstance().format(v);
    }

    // ---------------------------------------------------------------- display

    void refresh() {
        FuelLog.Stats s = log.stats();
        avg.setText(consumption(s.avgL100));
        avgUnit.setText(consumptionUnit() + " average");
        last.setText(consumption(s.lastL100));
        best.setText(consumption(s.bestL100));
        costPerKm.setText(Double.isNaN(s.costPerKm) ? "--"
                : money(imperial() ? s.costPerKm / MI_PER_KM : s.costPerKm) + " / " + distUnit());
        month.setText(s.fillUps == 0 ? "--" : money(s.monthCost));
        total.setText(s.fillUps == 0 ? "--" : money(s.totalCost));
        range.setText(Double.isNaN(s.avgKmPerFill) ? "--"
                : String.format(Locale.getDefault(), "%.0f %s", dist(s.avgKmPerFill), distUnit()));
        list.setAdapter(new Rows(log.entries()));
    }

    private final class Rows extends ArrayAdapter<FuelLog.Entry> {
        private final DateFormat date = DateFormat.getDateInstance(DateFormat.MEDIUM);

        Rows(List<FuelLog.Entry> items) { super(act, 0, items); }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : LayoutInflater.from(act).inflate(R.layout.fuel_row, parent, false);
            FuelLog.Entry e = getItem(position);
            ((TextView) v.findViewById(R.id.row_date)).setText(date.format(new Date(e.time)));
            ((TextView) v.findViewById(R.id.row_odo)).setText(String.format(Locale.getDefault(),
                    "%.0f %s%s", dist(e.odoKm), distUnit(), e.full ? "" : " · partial"));
            ((TextView) v.findViewById(R.id.row_fuel)).setText(String.format(Locale.getDefault(),
                    "%.1f %s", vol(e.litres), volUnit()));
            ((TextView) v.findViewById(R.id.row_cost)).setText(money(e.cost));
            ((TextView) v.findViewById(R.id.row_eff)).setText(
                    Double.isNaN(e.l100) ? "" : consumption(e.l100) + " " + consumptionUnit());
            return v;
        }
    }

    // ---------------------------------------------------------------- input

    private EditText field(LinearLayout box, String hint, String prefill) {
        EditText f = new EditText(act);
        f.setHint(hint);
        f.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        f.setSingleLine(true);
        if (prefill != null) f.setText(prefill);
        box.addView(f);
        return f;
    }

    private static double num(EditText f) {
        try {
            return Double.parseDouble(f.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * act.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        double lastOdo = log.lastOdometerKm();
        EditText odo = field(box, "Odometer (" + distUnit() + ")",
                Double.isNaN(lastOdo) ? null : String.format(Locale.US, "%.0f", dist(lastOdo)));
        EditText fuel = field(box, "Fuel added (" + volUnit() + ")", null);
        EditText cost = field(box, "Total cost", null);
        CheckBox full = new CheckBox(act);
        full.setText("Filled the tank to full");
        full.setChecked(true);
        box.addView(full);

        AlertDialog d = new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add fill-up")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double o = num(odo), f = num(fuel), c = num(cost);
            if (Double.isNaN(o) || Double.isNaN(f) || Double.isNaN(c) || f <= 0 || o < 0 || c < 0) {
                Toast.makeText(act, "Enter the odometer, fuel and cost", Toast.LENGTH_SHORT).show();
                return;
            }
            FuelLog.Entry e = new FuelLog.Entry();
            e.time = System.currentTimeMillis();
            e.odoKm = toKm(o);
            e.litres = toLitres(f);
            e.cost = c;
            e.full = full.isChecked();
            log.add(e);
            refresh();
            d.dismiss();
        });
    }

    private void confirmDelete(FuelLog.Entry e) {
        new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Delete this fill-up?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dlg, w) -> { log.remove(e); refresh(); })
                .show();
    }
}

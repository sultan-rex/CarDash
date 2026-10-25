package com.sulthan.cardash;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** A month calendar of drives: tap a day for its distance, top speed and time on the road. */
public class DrivesActivity extends Activity {
    private Prefs prefs;
    private DriveLog log;
    private CalendarGridView grid;
    private TextView month, date, km, kmUnit, top, time, avg, monthKm, monthDays, monthTop, empty;
    private int year, month0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drives);
        prefs = new Prefs(this);
        log = DriveLog.get(this);
        grid = findViewById(R.id.d_grid);
        month = findViewById(R.id.d_month);
        date = findViewById(R.id.d_date);
        km = findViewById(R.id.d_km);
        kmUnit = findViewById(R.id.d_km_unit);
        top = findViewById(R.id.d_top);
        time = findViewById(R.id.d_time);
        avg = findViewById(R.id.d_avg);
        monthKm = findViewById(R.id.d_month_km);
        monthDays = findViewById(R.id.d_month_days);
        monthTop = findViewById(R.id.d_month_top);
        empty = findViewById(R.id.d_empty);

        findViewById(R.id.d_back).setOnClickListener(v -> finish());
        findViewById(R.id.d_prev).setOnClickListener(v -> shift(-1));
        findViewById(R.id.d_next).setOnClickListener(v -> shift(1));
        grid.setOnDay(this::showDay);

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month0 = now.get(Calendar.MONTH);
    }

    @Override
    protected void onStart() {
        super.onStart();
        log.flush(); // include what the recorder has counted so far
        grid.setLog(log, prefs.mph());
        showMonth(year, month0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) MainActivity.hideSystemBars(this);
    }

    private void shift(int by) {
        Calendar c = Calendar.getInstance();
        c.set(year, month0, 1);
        c.add(Calendar.MONTH, by);
        showMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
    }

    private void showMonth(int y, int m0) {
        year = y;
        month0 = m0;
        Calendar c = Calendar.getInstance();
        c.set(y, m0, 1);
        month.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.getTime()));
        grid.setMonth(y, m0);

        monthKm.setText(distance(log.monthKm(y, m0)));
        monthDays.setText(String.valueOf(log.monthDays(y, m0)));
        monthTop.setText(speed(log.monthTop(y, m0)));
        empty.setVisibility(log.isEmpty() ? View.VISIBLE : View.GONE);

        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.YEAR) == y && now.get(Calendar.MONTH) == m0) showDay(now.get(Calendar.DAY_OF_MONTH));
        else showDay(-1);
    }

    private void showDay(int d) {
        grid.setSelected(d);
        if (d < 1) {
            date.setText(month.getText());
            km.setText("--");
            top.setText("--");
            time.setText("--");
            avg.setText("--");
            return;
        }
        Calendar c = Calendar.getInstance();
        c.set(year, month0, d);
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(c.getTime()));
        DriveLog.Day day = log.day(DriveLog.key(year, month0, d));
        boolean drove = day != null && day.km > 0.05;
        kmUnit.setText(prefs.mph() ? "miles driven" : "km driven");
        km.setText(drove ? String.format(Locale.getDefault(), "%.1f", prefs.mph() ? day.km * 0.621371 : day.km) : "0");
        top.setText(drove ? speed(day.topKmh) : "--");
        time.setText(drove ? duration(day.movingSec) : "--");
        avg.setText(drove ? speed(day.avgKmh()) : "--");
    }

    private String distance(double km) {
        double v = prefs.mph() ? km * 0.621371 : km;
        return String.format(Locale.getDefault(), "%.1f %s", v, prefs.mph() ? "mi" : "km");
    }

    private String speed(double kmh) {
        return String.format(Locale.getDefault(), "%.0f %s", prefs.speed(kmh), prefs.speedUnit());
    }

    private static String duration(double sec) {
        long m = Math.round(sec / 60.0);
        return m >= 60 ? String.format(Locale.US, "%d h %02d min", m / 60, m % 60) : m + " min";
    }
}

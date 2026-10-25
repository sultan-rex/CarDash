package com.sulthan.cardash;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/** Full-screen engine gauges and fault code reader. */
public class GaugesActivity extends Activity implements ObdManager.Listener {
    private Prefs prefs;
    private GaugeView speed, rpm, coolant, throttle, load, volt;
    private ImageView obdDot;
    private TextView obdStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_gauges);
        prefs = new Prefs(this);

        speed = findViewById(R.id.g_speed);
        rpm = findViewById(R.id.g_rpm);
        coolant = findViewById(R.id.g_coolant);
        throttle = findViewById(R.id.g_throttle);
        load = findViewById(R.id.g_load);
        volt = findViewById(R.id.g_volt);
        obdDot = findViewById(R.id.obd_dot);
        obdStatus = findViewById(R.id.obd_status);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_dtc).setOnClickListener(v -> readCodes());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (prefs.mph()) speed.setRange(0, 140, Float.NaN, "mph");
        else speed.setRange(0, 220, Float.NaN, "km/h");
        if (prefs.fahrenheit()) coolant.setRange(100, 266, 221, "°F");
        else coolant.setRange(40, 130, 105, "°C");
        ObdManager.get().addListener(this);
    }

    @Override
    protected void onStop() {
        ObdManager.get().removeListener(this);
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) MainActivity.hideSystemBars(this);
    }

    @Override
    public void onObdData(ObdData d) {
        speed.setValue(Double.isNaN(d.speedKmh) ? d.speedKmh : prefs.speed(d.speedKmh));
        rpm.setValue(d.rpm);
        coolant.setValue(Double.isNaN(d.coolantC) ? d.coolantC : prefs.temp(d.coolantC));
        throttle.setValue(d.throttlePct);
        load.setValue(d.loadPct);
        volt.setValue(d.voltage);
    }

    @Override
    public void onObdState(ObdManager.State state, String message) {
        MainActivity.showObdState(obdDot, obdStatus, state, message);
    }

    private void readCodes() {
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Fault codes")
                .setMessage("Asking the engine computer…")
                .show();
        ObdManager.get().readDtcs((codes, message) -> {
            progress.dismiss();
            if (isFinishing()) return;
            if (codes == null) {
                new AlertDialog.Builder(this).setTitle("Couldn't read codes").setMessage(message)
                        .setPositiveButton("OK", null).show();
                return;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Fault codes");
            if (codes.isEmpty()) {
                b.setMessage("No stored fault codes.").setPositiveButton("OK", null);
            } else {
                b.setMessage(TextUtils.join("\n", codes)
                                + "\n\nLook these codes up for your car model to see what they mean.")
                        .setPositiveButton("OK", null)
                        .setNegativeButton("Clear codes", (dlg, w) -> confirmClear());
            }
            b.show();
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear fault codes?")
                .setMessage("This turns off the check-engine light and resets emissions readiness. "
                        + "Only do it with the engine off and ignition on, after the problem is fixed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dlg, w) -> ObdManager.get().clearDtcs(this::showClearResult))
                .show();
    }

    private void showClearResult(List<String> codes, String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this).setTitle("Fault codes").setMessage(message)
                .setPositiveButton("OK", null).show();
    }
}

package com.sulthan.cardash;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.Settings;

import com.andrerinas.openheadunit.host.AutoStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings("deprecation") // Framework preferences avoid pulling in AndroidX on this old tablet.
public class SettingsActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(ThemeMode.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new Fragment())
                .commit();
    }

    public static class Fragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);

            findPreference("home_screen").setOnPreferenceClickListener(p -> {
                open(new Intent(Settings.ACTION_HOME_SETTINGS));
                return true;
            });
            findPreference("media_access").setOnPreferenceClickListener(p -> {
                MainActivity.openMediaAccessSettings(getActivity());
                return true;
            });
            findPreference("obd_device").setOnPreferenceClickListener(p -> {
                loadPairedDevices();
                return false;
            });
            findPreference("clear_drives").setOnPreferenceClickListener(p -> {
                new android.app.AlertDialog.Builder(getActivity(), ThemeMode.dialogTheme(getActivity()))
                        .setTitle("Clear drive history?")
                        .setMessage("Every recorded day, distance and top speed will be deleted.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear", (d, w) -> DriveLog.get(getActivity()).clear())
                        .show();
                return true;
            });
            findPreference("aa_any_phone").setOnPreferenceChangeListener((p, value) -> {
                boolean on = (Boolean) value;
                AutoStart.setAnyPhone(getActivity(), on);
                ((android.preference.SwitchPreference) p).setChecked(on);
                updateSummaries();
                return false; // saved above, in the head unit's own settings
            });
            findPreference("aa_bt_phones").setOnPreferenceChangeListener((p, value) -> {
                @SuppressWarnings("unchecked")
                Set<String> macs = new java.util.HashSet<>((Set<String>) value);
                String first = macs.isEmpty() ? "" : plainName(macs.iterator().next());
                AutoStart.setDevices(getActivity(), macs, first);
                if (!macs.isEmpty()) {
                    getPreferenceManager().getSharedPreferences().edit()
                            .putStringSet(ConnectControls.SAVED_PHONES, macs).apply();
                }
                ((MultiSelectListPreference) p).setValues(macs);
                updateSummaries();
                return false; // saved above, in the head unit's own lists
            });
            findPreference("alert_test").setOnPreferenceClickListener(p -> {
                AlertOverlay.get(getActivity()).show("TEST", "Engine too hot",
                        "This is a test. Real warnings look like this, over any app.", AlertOverlay.RED, true, 8000);
                return true;
            });
            findPreference("export_csv").setOnPreferenceClickListener(p -> {
                try {
                    java.io.File dir = Backup.exportCsv(getActivity());
                    message("Exported", "drives.csv and fuel.csv are in " + dir.getPath()
                            + ".\n\nConnect the tablet to a computer to copy them.");
                } catch (java.io.IOException e) {
                    message("Export failed", String.valueOf(e.getMessage()));
                }
                return true;
            });
            findPreference("backup_export").setOnPreferenceClickListener(p -> {
                try {
                    java.io.File f = Backup.export(getActivity());
                    message("Backed up", "Saved to " + f.getPath() + ".\n\nA factory reset wipes this folder too, "
                            + "so copy the file to a computer (the Mac's restore script does this).");
                } catch (Exception e) {
                    message("Backup failed", String.valueOf(e.getMessage()));
                }
                updateSummaries();
                return true;
            });
            findPreference("backup_import").setOnPreferenceClickListener(p -> {
                java.io.File f = new java.io.File(Backup.folder(), Backup.BACKUP_FILE);
                if (!f.isFile()) {
                    message("No backup found", "Put " + Backup.BACKUP_FILE + " in " + Backup.folder().getPath() + " first.");
                    return true;
                }
                new android.app.AlertDialog.Builder(getActivity(), ThemeMode.dialogTheme(getActivity()))
                        .setTitle("Restore from backup?")
                        .setMessage("Every CarDash and Android Auto setting, the fuel log, drive history and service "
                                + "reminders are replaced by the backup from " + savedDate(f) + ". CarDash then restarts.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Restore", (d, w) -> restore(f))
                        .show();
                return true;
            });
            findPreference("crash_log").setOnPreferenceClickListener(p -> {
                String log = CrashGuard.readLog(getActivity());
                if (log == null) {
                    message("Crash log", "No crashes recorded.");
                    return true;
                }
                String tail = log.length() > 6000 ? "…" + log.substring(log.length() - 6000) : log;
                new android.app.AlertDialog.Builder(getActivity(), ThemeMode.dialogTheme(getActivity()))
                        .setTitle("Crash log")
                        .setMessage(tail)
                        .setPositiveButton("OK", null)
                        .setNegativeButton("Clear", (d, w) -> { CrashGuard.clearLog(getActivity()); updateSummaries(); })
                        .show();
                return true;
            });
            boolean debuggable = (getActivity().getApplicationInfo().flags
                    & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (debuggable) {
                findPreference("crash_test").setOnPreferenceClickListener(p -> {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        throw new RuntimeException("Crash recovery test (from Settings)");
                    });
                    return true;
                });
            } else {
                ((android.preference.PreferenceCategory) findPreference("cat_problems"))
                        .removePreference(findPreference("crash_test"));
            }
            loadPairedDevices();
            updateSummaries();
        }

        private void message(String title, String text) {
            new android.app.AlertDialog.Builder(getActivity(), ThemeMode.dialogTheme(getActivity()))
                    .setTitle(title).setMessage(text).setPositiveButton("OK", null).show();
        }

        private static String savedDate(java.io.File f) {
            return new java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(f.lastModified()));
        }

        private void restore(java.io.File f) {
            android.app.Activity act = getActivity();
            try {
                Backup.restore(act, f);
            } catch (Exception e) {
                message("Restore failed", String.valueOf(e.getMessage()));
                return;
            }
            // Every part of the app caches settings, so start it again from scratch.
            android.content.Context app = act.getApplicationContext();
            android.content.Intent i = new android.content.Intent(app, MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = android.app.PendingIntent.FLAG_CANCEL_CURRENT
                    | (android.os.Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
            android.app.AlarmManager am = (android.app.AlarmManager) app.getSystemService(ALARM_SERVICE);
            am.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1500,
                    android.app.PendingIntent.getActivity(app, 8, i, flags));
            android.widget.Toast.makeText(act, "Restored. CarDash restarts…", android.widget.Toast.LENGTH_SHORT).show();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> android.os.Process.killProcess(android.os.Process.myPid()), 600);
        }

        /**
         * Don't keep the preferences' screen state across a rebuild (theme change, restore). Android
         * 5's list of phones (a multi-choice list that is not saved in CarDash's settings) cannot
         * read its own saved state back and crashes the screen.
         */
        @Override
        public void onSaveInstanceState(Bundle out) {
            super.onSaveInstanceState(out);
            out.remove("android:preferences");
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            updateSummaries();
            if ("overlay_switch".equals(key)) SwitchOverlayService.sync(getActivity());
            if ("trip_tracking".equals(key) || key != null && key.startsWith("alert_")) TripService.sync(getActivity());
            if ("cast_receiver".equals(key)) CastService.sync(getActivity());
            if ("theme_mode".equals(key)) getActivity().recreate();
            if ("obd_enabled".equals(key) || "obd_device".equals(key)) {
                // Re-delivering the start command reconnects with the new settings.
                ObdService.sync(getActivity());
            }
        }

        private final java.util.Map<String, String> pairedNames = new java.util.HashMap<>();

        private String plainName(String mac) {
            String n = pairedNames.get(mac);
            return n == null ? mac : n;
        }

        private void loadPairedDevices() {
            ListPreference p = (ListPreference) findPreference("obd_device");
            List<String> names = new ArrayList<>();
            List<String> addrs = new ArrayList<>();
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            try {
                Set<BluetoothDevice> bonded = bt == null ? null : bt.getBondedDevices();
                if (bonded != null) {
                    for (BluetoothDevice d : bonded) {
                        String plain = d.getName() == null ? "Unknown" : d.getName();
                        pairedNames.put(d.getAddress(), plain);
                        names.add(plain + "  (" + d.getAddress() + ")");
                        addrs.add(d.getAddress());
                    }
                }
            } catch (SecurityException ignored) {}
            p.setEntries(names.toArray(new CharSequence[0]));
            p.setEntryValues(addrs.toArray(new CharSequence[0]));

            // Phones that start Android Auto: the same paired devices, current choice from the head unit.
            MultiSelectListPreference phones = (MultiSelectListPreference) findPreference("aa_bt_phones");
            phones.setEntries(names.toArray(new CharSequence[0]));
            phones.setEntryValues(addrs.toArray(new CharSequence[0]));
            phones.setValues(new java.util.HashSet<>(AutoStart.getMacs(getActivity())));
            ((android.preference.SwitchPreference) findPreference("aa_any_phone"))
                    .setChecked(AutoStart.isAnyPhone(getActivity()));
        }

        private void updateSummaries() {
            ListPreference dev = (ListPreference) findPreference("obd_device");
            CharSequence entry = dev.getEntry();
            dev.setSummary(entry != null ? entry
                    : dev.getEntries() == null || dev.getEntries().length == 0
                    ? "Pair your ELM327 in Android Bluetooth settings first (PIN is usually 1234 or 0000)"
                    : "Choose your ELM327 adapter");

            MultiSelectListPreference phones = (MultiSelectListPreference) findPreference("aa_bt_phones");
            Set<String> chosen = phones.getValues();
            if (chosen == null || chosen.isEmpty()) {
                phones.setSummary("None. Choose the phones that should start Android Auto when they connect over Bluetooth");
            } else {
                List<String> label = new ArrayList<>();
                for (String mac : chosen) label.add(plainName(mac));
                phones.setSummary(android.text.TextUtils.join(", ", label));
            }

            EditTextPreference speed = (EditTextPreference) findPreference(Alerts.PREF_SPEED);
            String sv = speed.getText();
            String unit = new Prefs(getActivity()).speedUnit();
            speed.setDialogTitle("Speed alert (" + unit + ", blank for off)");
            speed.setSummary(sv == null || sv.trim().isEmpty() ? "Off. Set a speed to get a chime when you go faster"
                    : "Chime above " + sv.trim() + " " + unit);

            java.io.File bf = new java.io.File(Backup.folder(), Backup.BACKUP_FILE);
            findPreference("backup_export").setSummary("Save everything to " + bf.getPath());
            findPreference("backup_import").setSummary(bf.isFile() ? "Backup from " + savedDate(bf) : "No backup file yet");
            findPreference("crash_log").setSummary(CrashGuard.readLog(getActivity()) == null
                    ? "No crashes recorded" : "Crashes recorded; tap to read");

            EditTextPreference ssid = (EditTextPreference) findPreference("aa_ssid");
            String s = ssid.getText();
            ssid.setSummary(s == null || s.trim().isEmpty() ? "Not set" : s);
        }

        private void open(Intent i) {
            try {
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }
}

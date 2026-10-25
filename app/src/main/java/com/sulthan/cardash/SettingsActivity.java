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
                new android.app.AlertDialog.Builder(getActivity(), android.R.style.Theme_Material_Dialog_Alert)
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
            loadPairedDevices();
            updateSummaries();
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
            if ("trip_tracking".equals(key)) TripService.sync(getActivity());
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

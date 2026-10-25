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
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.Settings;

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
            if ("obd_enabled".equals(key) || "obd_device".equals(key) || "demo_mode".equals(key)) {
                // Re-delivering the start command reconnects with the new settings.
                ObdService.sync(getActivity());
            }
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
                        names.add((d.getName() == null ? "Unknown" : d.getName()) + "  (" + d.getAddress() + ")");
                        addrs.add(d.getAddress());
                    }
                }
            } catch (SecurityException ignored) {}
            p.setEntries(names.toArray(new CharSequence[0]));
            p.setEntryValues(addrs.toArray(new CharSequence[0]));
        }

        private void updateSummaries() {
            ListPreference dev = (ListPreference) findPreference("obd_device");
            CharSequence entry = dev.getEntry();
            dev.setSummary(entry != null ? entry
                    : dev.getEntries() == null || dev.getEntries().length == 0
                    ? "Pair your ELM327 in Android Bluetooth settings first (PIN is usually 1234 or 0000)"
                    : "Choose your ELM327 adapter");

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

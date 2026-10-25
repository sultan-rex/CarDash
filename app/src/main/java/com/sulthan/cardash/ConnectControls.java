package com.sulthan.cardash;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.Toast;

import com.andrerinas.openheadunit.host.AutoStart;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wi-Fi and Bluetooth buttons for the home screen. Each icon shows the current state; tapping
 * opens a list to connect. From Android 10 apps may no longer switch Wi-Fi or connect devices,
 * so there the dialogs hand over to the system settings.
 */
final class ConnectControls {
    private final Activity act;
    /** The phones last chosen by hand, kept so "Chosen phones" can be switched back on. */
    static final String SAVED_PHONES = "aa_saved_macs";

    private final ImageView wifiIcon, btIcon, autoIcon, locIcon;
    private final SharedPreferences prefs;
    private final WifiManager wifi;
    private final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
    private final int on, off, connected;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    ConnectControls(Activity act) {
        this.act = act;
        wifi = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiIcon = act.findViewById(R.id.btn_wifi);
        btIcon = act.findViewById(R.id.btn_bt);
        autoIcon = act.findViewById(R.id.btn_autostart);
        locIcon = act.findViewById(R.id.btn_location);
        locIcon.setOnClickListener(v -> toggleLocation());
        prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(act);
        autoIcon.setOnClickListener(v -> showAutoStart());
        on = GaugeView.color(act, R.color.text);
        off = GaugeView.color(act, R.color.track);
        connected = GaugeView.color(act, R.color.accent);
        wifiIcon.setOnClickListener(v -> showWifi());
        btIcon.setOnClickListener(v -> showBluetooth());
        if (bt == null) btIcon.setVisibility(android.view.View.GONE);
    }

    void start() {
        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(android.location.LocationManager.PROVIDERS_CHANGED_ACTION);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        act.registerReceiver(stateReceiver, f);
        refresh();
    }

    void stop() {
        try { act.unregisterReceiver(stateReceiver); } catch (IllegalArgumentException ignored) { }
    }

    private static void tint(ImageView v, int color) { v.setImageTintList(ColorStateList.valueOf(color)); }

    /** 0 = off, 1 = chosen phones, 2 = any phone. */
    private int autoMode() {
        if (AutoStart.isAnyPhone(act)) return 2;
        return AutoStart.getMacs(act).isEmpty() ? 0 : 1;
    }

    // ---------------------------------------------------------------- Location

    private boolean locationOn() {
        android.location.LocationManager lm = (android.location.LocationManager) act.getSystemService(Context.LOCATION_SERVICE);
        try {
            return lm != null && lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Switches GPS location on or off (only GPS: the network provider makes Google ask about location sharing). That needs the WRITE_SECURE_SETTINGS permission, which can only be given
     * over adb; without it the button opens the system Location screen instead.
     */
    @SuppressWarnings("deprecation")
    private void toggleLocation() {
        boolean turnOn = !locationOn();
        try {
            android.content.ContentResolver cr = act.getContentResolver();
            Settings.Secure.setLocationProviderEnabled(cr, android.location.LocationManager.GPS_PROVIDER, turnOn);
            Toast.makeText(act, turnOn ? "Location on" : "Location off", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(act, turnOn ? "Turn Location on" : "Turn Location off", Toast.LENGTH_SHORT).show();
            openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        }
        refresh();
    }

    void refresh() {
        tint(locIcon, locationOn() ? connected : off);
        int mode = autoMode();
        tint(autoIcon, mode == 2 ? connected : mode == 1 ? on : off);
        boolean wifiOn = wifi != null && wifi.isWifiEnabled();
        tint(wifiIcon, !wifiOn ? off : currentSsid() != null ? connected : on);
        if (bt != null) {
            boolean btOn = bt.isEnabled();
            tint(btIcon, !btOn ? off : bt.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                    || bt.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
                    ? connected : on);
        }
    }

    private AlertDialog.Builder dialog(String title) {
        return new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert).setTitle(title);
    }

    private void openSettings(String action) {
        try {
            act.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(act, "Open Settings to continue", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- Android Auto auto-start

    private String phoneNames(Set<String> macs) {
        List<String> names = new ArrayList<>();
        if (bt != null) {
            try {
                for (BluetoothDevice d : bt.getBondedDevices()) {
                    if (macs.contains(d.getAddress())) names.add(d.getName() == null ? d.getAddress() : d.getName());
                }
            } catch (SecurityException ignored) { }
        }
        return names.isEmpty() ? "none chosen yet" : android.text.TextUtils.join(", ", names);
    }

    private void showAutoStart() {
        Set<String> saved = new HashSet<>(prefs.getStringSet(SAVED_PHONES, new HashSet<String>()));
        Set<String> current = AutoStart.getMacs(act);
        if (!current.isEmpty()) saved = new HashSet<>(current);
        final Set<String> chosen = saved;
        String[] labels = {"Off", "Chosen phones (" + phoneNames(chosen) + ")", "Any phone"};
        dialog("Start Android Auto when a phone connects over Bluetooth")
                .setSingleChoiceItems(labels, autoMode(), (d, which) -> {
                    applyAutoStart(which, chosen);
                    d.dismiss();
                })
                .setNeutralButton("Choose phones", (d, w) ->
                        act.startActivity(new Intent(act, SettingsActivity.class)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void applyAutoStart(int mode, Set<String> chosen) {
        if (!chosen.isEmpty()) prefs.edit().putStringSet(SAVED_PHONES, chosen).apply();
        if (mode == 0) {
            AutoStart.setAnyPhone(act, false);
            AutoStart.setDevices(act, new HashSet<String>(), "");
        } else if (mode == 1) {
            if (chosen.isEmpty()) {
                Toast.makeText(act, "Choose which phones first", Toast.LENGTH_SHORT).show();
                act.startActivity(new Intent(act, SettingsActivity.class));
                return;
            }
            AutoStart.setAnyPhone(act, false);
            AutoStart.setDevices(act, chosen, firstName(chosen));
        } else {
            AutoStart.setAnyPhone(act, true);
            AutoStart.setDevices(act, new HashSet<String>(), "");
        }
        refresh();
        Toast.makeText(act, mode == 0 ? "Auto-start off" : mode == 1 ? "Auto-start: chosen phones"
                : "Auto-start: any phone", Toast.LENGTH_SHORT).show();
    }

    private String firstName(Set<String> macs) {
        String names = phoneNames(macs);
        int comma = names.indexOf(',');
        return comma < 0 ? names : names.substring(0, comma);
    }

    // ---------------------------------------------------------------- Wi-Fi

    private String currentSsid() {
        if (wifi == null || !wifi.isWifiEnabled()) return null;
        WifiInfo info = wifi.getConnectionInfo();
        if (info == null || info.getNetworkId() == -1) return null;
        String s = info.getSSID();
        return s == null || s.equals("<unknown ssid>") ? null : s.replace("\"", "");
    }

    @SuppressWarnings("deprecation") // Wi-Fi switching is only possible below Android 10.
    private void showWifi() {
        if (wifi == null) return;
        boolean canControl = Build.VERSION.SDK_INT < 29;
        if (!canControl) { openSettings(Settings.ACTION_WIFI_SETTINGS); return; }

        boolean enabled = wifi.isWifiEnabled();
        final List<WifiConfiguration> nets = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String now = currentSsid();
        if (enabled) {
            List<WifiConfiguration> saved = wifi.getConfiguredNetworks();
            if (saved != null) for (WifiConfiguration c : saved) {
                if (c.SSID == null) continue;
                String name = c.SSID.replace("\"", "");
                nets.add(c);
                labels.add(name.equals(now) ? name + "   ● connected" : name);
            }
        }
        AlertDialog.Builder b = dialog(enabled ? "Wi-Fi" : "Wi-Fi is off")
                .setNegativeButton("Wi-Fi settings", (d, w) -> openSettings(Settings.ACTION_WIFI_SETTINGS))
                .setNeutralButton(enabled ? "Turn off" : "Turn on", (d, w) -> {
                    wifi.setWifiEnabled(!enabled);
                    refresh();
                });
        if (enabled && !labels.isEmpty()) {
            b.setItems(labels.toArray(new String[0]), (d, which) -> {
                WifiConfiguration c = nets.get(which);
                wifi.enableNetwork(c.networkId, true);
                wifi.reconnect();
                Toast.makeText(act, "Connecting to " + c.SSID.replace("\"", ""), Toast.LENGTH_SHORT).show();
            });
        } else if (enabled) {
            b.setMessage("No saved networks. Use Wi-Fi settings to add one.");
        }
        b.show();
    }

    // ---------------------------------------------------------------- Bluetooth

    @SuppressWarnings("deprecation") // enable()/disable() are restricted from Android 13.
    private void showBluetooth() {
        if (bt == null) return;
        boolean enabled = bt.isEnabled();
        AlertDialog.Builder b = dialog(enabled ? "Bluetooth" : "Bluetooth is off")
                .setNegativeButton("Pair new", (d, w) -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS))
                .setNeutralButton(enabled ? "Turn off" : "Turn on", (d, w) -> {
                    if (Build.VERSION.SDK_INT >= 33) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS); return; }
                    if (enabled) bt.disable(); else bt.enable();
                    refresh();
                });
        if (enabled) {
            final List<BluetoothDevice> devices = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            try {
                for (BluetoothDevice dev : bt.getBondedDevices()) {
                    devices.add(dev);
                    String name = dev.getName() == null ? dev.getAddress() : dev.getName();
                    labels.add(isConnected(dev) ? name + "   ● connected" : name);
                }
            } catch (SecurityException e) {
                Toast.makeText(act, "Allow Bluetooth access in app permissions", Toast.LENGTH_LONG).show();
            }
            if (labels.isEmpty()) b.setMessage("No paired devices. Tap Pair new to add one.");
            else b.setItems(labels.toArray(new String[0]), (d, which) -> toggleConnection(devices.get(which)));
        }
        b.show();
    }

    private boolean isConnected(BluetoothDevice dev) {
        try {
            Method m = BluetoothDevice.class.getMethod("isConnected");
            return (Boolean) m.invoke(dev);
        } catch (Exception e) {
            return false;
        }
    }

    /** Connects (or disconnects) the audio profiles of a paired device. */
    private void toggleConnection(BluetoothDevice dev) {
        if (Build.VERSION.SDK_INT >= 29) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS); return; }
        final boolean disconnect = isConnected(dev);
        final String name = dev.getName() == null ? dev.getAddress() : dev.getName();
        Toast.makeText(act, (disconnect ? "Disconnecting " : "Connecting to ") + name, Toast.LENGTH_SHORT).show();
        for (int profile : new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET}) {
            bt.getProfileProxy(act, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int p, BluetoothProfile proxy) {
                    try {
                        Method m = proxy.getClass().getMethod(disconnect ? "disconnect" : "connect", BluetoothDevice.class);
                        m.invoke(proxy, dev);
                    } catch (Exception e) {
                        openSettings(Settings.ACTION_BLUETOOTH_SETTINGS);
                    } finally {
                        bt.closeProfileProxy(p, proxy);
                    }
                }

                @Override public void onServiceDisconnected(int p) { }
            }, profile);
        }
    }
}

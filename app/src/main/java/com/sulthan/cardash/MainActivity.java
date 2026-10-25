package com.sulthan.cardash;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements ObdManager.Listener {
    private static final String[] NAV_APPS = {
            "com.google.android.apps.maps", "app.organicmaps", "net.osmand", "net.osmand.plus", "com.waze"};
    private static final String[] MUSIC_APPS = {
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.google.android.music", "com.android.music"};

    private Prefs prefs;
    private TextView statSpeed, statSpeedLabel, statRpm, statCoolant, statVolt, obdStatus;
    private ImageView obdDot, npArt;
    private TextView npTitle, npArtist;
    private ImageButton npPlay;

    private double gpsSpeedKmh = Double.NaN;
    private ObdData lastObd = new ObdData();

    private MediaSessionManager sessions;
    private MediaController controller;
    private boolean mediaAccess;

    private boolean launchedForHotspot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.cd_activity_main);
        prefs = new Prefs(this);

        statSpeed = findViewById(R.id.stat_speed);
        statSpeedLabel = findViewById(R.id.stat_speed_label);
        statRpm = findViewById(R.id.stat_rpm);
        statCoolant = findViewById(R.id.stat_coolant);
        statVolt = findViewById(R.id.stat_volt);
        obdStatus = findViewById(R.id.obd_status);
        obdDot = findViewById(R.id.obd_dot);
        npArt = findViewById(R.id.np_art);
        npTitle = findViewById(R.id.np_title);
        npArtist = findViewById(R.id.np_artist);
        npPlay = findViewById(R.id.np_play);

        findViewById(R.id.tile_aa).setOnClickListener(v -> openAndroidAuto());
        findViewById(R.id.tile_nav).setOnClickListener(v -> openNavigation());
        findViewById(R.id.tile_music).setOnClickListener(v -> openMusic());
        findViewById(R.id.tile_gauges).setOnClickListener(v -> startActivity(new Intent(this, GaugesActivity.class)));
        findViewById(R.id.stats).setOnClickListener(v -> startActivity(new Intent(this, GaugesActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.now_playing).setOnClickListener(v -> { if (!mediaAccess) openMediaAccessSettings(this); });

        findViewById(R.id.np_prev).setOnClickListener(v -> { if (controller != null) controller.getTransportControls().skipToPrevious(); });
        findViewById(R.id.np_next).setOnClickListener(v -> { if (controller != null) controller.getTransportControls().skipToNext(); });
        npPlay.setOnClickListener(v -> togglePlay());

        sessions = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        requestRuntimePermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObdService.sync(this);
        ObdManager.get().addListener(this);
        startGps();
        connectMedia();
        statSpeedLabel.setText(prefs.speedUnit());
        render();
    }

    @Override
    protected void onStop() {
        ObdManager.get().removeListener(this);
        stopGps();
        disconnectMedia();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(wifiReceiver);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // As a home screen there is nothing to go back to.
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars(this);
    }

    static void hideSystemBars(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 23) return; // granted at install time
        String[] perms = Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        requestPermissions(perms, 1);
    }

    // ---------------------------------------------------------------- OBD + GPS

    @Override
    public void onObdData(ObdData data) {
        lastObd = data;
        render();
    }

    @Override
    public void onObdState(ObdManager.State state, String message) {
        showObdState(obdDot, obdStatus, state, message);
    }

    static void showObdState(ImageView dot, TextView label, ObdManager.State state, String message) {
        Context ctx = dot.getContext();
        int c;
        switch (state) {
            case CONNECTED: c = GaugeView.color(ctx, R.color.ok); break;
            case DEMO: c = GaugeView.color(ctx, R.color.accent); break;
            case CONNECTING: c = 0xFFFFC857; break;
            default: c = GaugeView.color(ctx, R.color.text_dim);
        }
        dot.setColorFilter(c);
        label.setText(message);
    }

    private void render() {
        // OBD speed is more accurate; fall back to GPS when there's no adapter.
        double speed = !Double.isNaN(lastObd.speedKmh) ? lastObd.speedKmh : gpsSpeedKmh;
        statSpeed.setText(fmt(Double.isNaN(speed) ? speed : prefs.speed(speed), 0));
        statRpm.setText(fmt(lastObd.rpm, 0));
        statCoolant.setText(Double.isNaN(lastObd.coolantC) ? "--"
                : fmt(prefs.temp(lastObd.coolantC), 0) + prefs.tempUnit());
        statVolt.setText(Double.isNaN(lastObd.voltage) ? "--" : fmt(lastObd.voltage, 1) + "V");
    }

    static String fmt(double v, int decimals) {
        return Double.isNaN(v) ? "--" : String.format(Locale.US, "%." + decimals + "f", v);
    }

    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            gpsSpeedKmh = l.hasSpeed() ? l.getSpeed() * 3.6 : Double.NaN;
            render();
        }
        @Override public void onStatusChanged(String p, int s, Bundle b) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) { gpsSpeedKmh = Double.NaN; render(); }
    };

    private void startGps() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
        } catch (SecurityException | IllegalArgumentException ignored) {
            // No permission or no GPS: the speed tile just shows "--" until OBD connects.
        }
    }

    private void stopGps() {
        ((LocationManager) getSystemService(LOCATION_SERVICE)).removeUpdates(gpsListener);
    }

    // ---------------------------------------------------------------- media

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pickController;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { renderMedia(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { renderMedia(); }
        @Override public void onSessionDestroyed() { connectMedia(); }
    };

    private void connectMedia() {
        ComponentName listener = new ComponentName(this, MediaListenerService.class);
        try {
            List<MediaController> list = sessions.getActiveSessions(listener);
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener);
            mediaAccess = true;
            pickController(list);
        } catch (SecurityException e) {
            mediaAccess = false;
            setController(null);
        }
    }

    private void disconnectMedia() {
        if (mediaAccess) sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        setController(null);
    }

    private void pickController(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            for (MediaController mc : list) {
                PlaybackState s = mc.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { pick = mc; break; }
            }
            if (pick == null && !list.isEmpty()) pick = list.get(0);
        }
        setController(pick);
    }

    private void setController(MediaController mc) {
        if (controller != null) controller.unregisterCallback(mediaCallback);
        controller = mc;
        if (controller != null) controller.registerCallback(mediaCallback);
        renderMedia();
    }

    private boolean isPlaying() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    private void togglePlay() {
        if (controller == null) { openMusic(); return; }
        if (isPlaying()) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    private void renderMedia() {
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        if (!mediaAccess) {
            npTitle.setText("Tap to show music here");
            npArtist.setText("Allow CarDash now-playing access");
        } else if (md == null) {
            npTitle.setText("Nothing playing");
            npArtist.setText("");
        } else {
            npTitle.setText(nonNull(md.getString(MediaMetadata.METADATA_KEY_TITLE)));
            npArtist.setText(nonNull(md.getString(MediaMetadata.METADATA_KEY_ARTIST)));
        }
        Bitmap art = md == null ? null : md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null && md != null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art != null) {
            npArt.setImageTintList(null);
            npArt.setImageBitmap(art);
        } else {
            npArt.setImageResource(R.drawable.ic_music);
            npArt.setImageTintList(ColorStateList.valueOf(GaugeView.color(this, R.color.track)));
        }
        npPlay.setImageResource(isPlaying() ? R.drawable.ic_pause : R.drawable.cd_ic_play);
    }

    private static String nonNull(String s) { return s == null ? "" : s; }

    static void openMediaAccessSettings(Context ctx) {
        try {
            ctx.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, "Open Settings > Sound & notification > Notification access", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- launching apps

    private boolean launchPackage(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        startActivity(i);
        return true;
    }

    private void openPlayStore(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }

    private void openAndroidAuto() {
        startActivity(new Intent(this, com.andrerinas.openheadunit.main.MainActivity.class));
    }

    private void openNavigation() {
        String pref = prefs.navApp();
        if (!"auto".equals(pref)) {
            if (!launchPackage(pref)) openPlayStore(pref);
            return;
        }
        for (String pkg : NAV_APPS) if (launchPackage(pkg)) return;
        Toast.makeText(this, "No navigation app found. An offline maps app works best without mobile data.",
                Toast.LENGTH_LONG).show();
        openPlayStore("app.organicmaps");
    }

    private void openMusic() {
        // Prefer whichever app is currently playing.
        if (controller != null && launchPackage(controller.getPackageName())) return;
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (ActivityNotFoundException ignored) {}
        for (String pkg : MUSIC_APPS) if (launchPackage(pkg)) return;
        Toast.makeText(this, "No music app found", Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- wireless Android Auto

    /**
     * Open Headunit's Wi-Fi mode connects to the phone over its hotspot. When the tablet
     * joins the configured hotspot we open Open Headunit once for that connection.
     */
    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (info == null) return;
            if (!info.isConnected()) {
                launchedForHotspot = false;
                return;
            }
            if (launchedForHotspot || !prefs.aaAutoLaunch() || prefs.aaSsid().isEmpty()) return;
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            String ssid = wm.getConnectionInfo().getSSID();
            if (ssid == null) return;
            ssid = ssid.replace("\"", "");
            if (ssid.equalsIgnoreCase(prefs.aaSsid())) {
                launchedForHotspot = true;
                openAndroidAuto();
            }
        }
    };
}

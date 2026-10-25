package com.sulthan.cardash;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements ObdManager.Listener {
    private static final String[] MUSIC_APPS = {
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.google.android.music", "com.android.music"};
    private Prefs prefs;
    private TextView statSpeed, statSpeedLabel, statRpm, statCoolant, statVolt, obdStatus;
    private ImageView obdDot, npArt;
    private TextView npTitle, npArtist;
    private ImageButton npPlay;
    private PagerView pager;
    private View[] dots;
    private VerticalSliderView slVolume, slBrightness;
    private ImageButton btnMute, btnDim;
    private android.media.AudioManager audio;
    private boolean slidersBusy; // a finger is on a slider, so do not fight it with a refresh
    private TextView sTopDay, sTopMonth, sKmDay, sKmDayLabel;
    private View aaTile;
    private ImageView aaIcon;
    private TextView aaStatus;
    private Boolean aaShown; // what the tile currently shows; null until first drawn
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable aaPoll = new Runnable() {
        @Override public void run() {
            updateAaTile();
            updateDriveStats();
            syncSliders();
            ui.postDelayed(this, 1500);
        }
    };
    private FuelPage fuel;
    private ConnectControls connect;
    private SpeedoView speedo;
    private GaugeView gRpm, gCoolant, gThrottle, gLoad, gVolt;

    private double gpsSpeedKmh = Double.NaN;
    private ObdData lastObd = new ObdData();
    private boolean obdConnected;

    private MediaSessionManager sessions;
    private MediaController controller;
    private boolean mediaAccess;

    private boolean launchedForHotspot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A car dashboard: show over a simple (no PIN) lock screen when it wakes. Staying lit is only
        // while charging (see applyKeepAwake); on battery the screen times out as usual.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
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

        sTopDay = findViewById(R.id.s_top_day);
        sTopMonth = findViewById(R.id.s_top_month);
        sKmDay = findViewById(R.id.s_km_day);
        sKmDayLabel = findViewById(R.id.s_km_day_label);
        findViewById(R.id.btn_drives).setOnClickListener(v -> startActivity(new Intent(this, DrivesActivity.class)));
        setupSliders();
        aaTile = findViewById(R.id.tile_aa);
        aaIcon = findViewById(R.id.tile_aa_icon);
        aaStatus = findViewById(R.id.tile_aa_status);
        aaTile.setOnClickListener(v -> openAndroidAuto());
        pager = findViewById(R.id.pager);
        dots = new View[]{findViewById(R.id.dot_0), findViewById(R.id.dot_1), findViewById(R.id.dot_2)};
        pager.setOnPageChange(page -> {
            for (int i = 0; i < dots.length; i++) dots[i].setAlpha(i == page ? 1f : 0.3f);
        });
        speedo = findViewById(R.id.g_speedo);
        gRpm = findViewById(R.id.g_rpm);
        gCoolant = findViewById(R.id.g_coolant);
        gThrottle = findViewById(R.id.g_throttle);
        gLoad = findViewById(R.id.g_load);
        gVolt = findViewById(R.id.g_volt);
        findViewById(R.id.tile_gauges).setOnClickListener(v -> pager.goTo(1));
        findViewById(R.id.stats).setOnClickListener(v -> pager.goTo(1));
        findViewById(R.id.btn_dtc).setOnClickListener(v -> readCodes());
        fuel = new FuelPage(this, prefs);
        connect = new ConnectControls(this);
        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.now_playing).setOnClickListener(v -> { if (!mediaAccess) openMediaAccessSettings(this); });

        findViewById(R.id.np_prev).setOnClickListener(v -> { if (controller != null) controller.getTransportControls().skipToPrevious(); });
        findViewById(R.id.np_next).setOnClickListener(v -> { if (controller != null) controller.getTransportControls().skipToNext(); });
        npPlay.setOnClickListener(v -> togglePlay());

        sessions = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        requestRuntimePermissions();
    }

    private final BroadcastReceiver plugReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) { applyKeepAwake(); }
    };

    /** Keeps the screen on while the charger is connected; on battery it sleeps after the timeout. */
    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) { syncSliders(); }
    };

    /** Lowest brightness the slider can set, so the screen can never be turned fully black. */
    /**
     * The brightness slider's lower part (0 to DIM_ZONE percent) is the software dimmer, with the backlight held at
     * its minimum; above it the real backlight rises from its minimum to its maximum.
     */
    private static final int DIM_ZONE = 30;
    /** The backlight range Android allows on this tablet (10 to 237); filled in from the system when possible. */
    private int sysMin = 10, sysMax = 255;

    @SuppressWarnings("JavaReflectionMemberAccess")
    private void readBrightnessRange() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            int min = (Integer) android.os.PowerManager.class.getMethod("getMinimumScreenBrightnessSetting").invoke(pm);
            int max = (Integer) android.os.PowerManager.class.getMethod("getMaximumScreenBrightnessSetting").invoke(pm);
            if (min >= 0 && max > min) { sysMin = min; sysMax = max; }
        } catch (Exception ignored) { /* keep the defaults */ }
    }

    /** Slider position 0-100 from what the screen is doing now. */
    private int brightnessPercent() {
        int dark = DimService.darkness(this);
        if (dark > 0) return Math.round(DIM_ZONE * (1f - dark / (float) DimService.MAX_DARKNESS));
        int b = currentBrightness();
        int p = DIM_ZONE + (b - sysMin) * (100 - DIM_ZONE) / Math.max(1, sysMax - sysMin);
        return Math.max(DIM_ZONE, Math.min(100, p));
    }

    private void setBrightnessPercent(int p) {
        if (p < DIM_ZONE) {
            setBacklight(sysMin);
            DimService.setDarkness(this, Math.round(DimService.MAX_DARKNESS * (1f - p / (float) DIM_ZONE)));
        } else {
            DimService.setDarkness(this, 0);
            setBacklight(sysMin + (p - DIM_ZONE) * (sysMax - sysMin) / (100 - DIM_ZONE));
        }
    }

    private void setupSliders() {
        readBrightnessRange();
        audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        slVolume = findViewById(R.id.sl_volume);
        slBrightness = findViewById(R.id.sl_brightness);
        slVolume.setMax(audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
        slBrightness.setMax(100);
        btnMute = findViewById(R.id.btn_mute);
        btnDim = findViewById(R.id.btn_dim);
        btnMute.setOnClickListener(v -> toggleMute());
        btnDim.setOnClickListener(v -> toggleDim());
        slVolume.setOnTouchState(touching -> slidersBusy = touching);
        slBrightness.setOnTouchState(touching -> slidersBusy = touching);
        slVolume.setOnChange((p, fromUser) -> {
            if (fromUser) audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, p, 0);
        });
        slBrightness.setOnChange((p, fromUser) -> {
            if (fromUser) setBrightnessPercent(p);
        });
    }

    private static final String PREV_VOLUME = "prev_volume", PREV_BRIGHTNESS = "prev_brightness_pct";

    private int currentBrightness() {
        return android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS, 128);
    }

    /** Mutes by dropping the media volume to zero, remembering the level; tapping again brings it back. */
    private void toggleMute() {
        int stream = android.media.AudioManager.STREAM_MUSIC;
        int now = audio.getStreamVolume(stream);
        android.content.SharedPreferences sp = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (now == 0) {
            int back = sp.getInt(PREV_VOLUME, 0);
            if (back <= 0) back = Math.max(1, audio.getStreamMaxVolume(stream) / 2);
            audio.setStreamVolume(stream, back, 0);
        } else {
            sp.edit().putInt(PREV_VOLUME, now).apply();
            audio.setStreamVolume(stream, 0, 0);
        }
        slidersBusy = false;
        syncSliders();
    }

    /** Goes to the darkest level, remembering where the slider was; tapping again brings it back. */
    private void toggleDim() {
        int now = brightnessPercent();
        android.content.SharedPreferences sp = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (now <= 0) {
            int back = sp.getInt(PREV_BRIGHTNESS, 0);
            setBrightnessPercent(back > 0 ? back : 60);
        } else {
            sp.edit().putInt(PREV_BRIGHTNESS, now).apply();
            setBrightnessPercent(0);
        }
        slidersBusy = false;
        syncSliders();
    }

    /** Teal icons while muted or dimmed, so it is clear why the sound or screen is off. */
    private void updateSliderButtons(boolean muted, boolean dimmed) {
        int accent = GaugeView.color(this, R.color.accent), dim = GaugeView.color(this, R.color.text_dim);
        btnMute.setImageResource(muted ? R.drawable.ic_volume_off : R.drawable.ic_volume);
        btnMute.setImageTintList(android.content.res.ColorStateList.valueOf(muted ? accent : dim));
        btnDim.setImageTintList(android.content.res.ColorStateList.valueOf(dimmed ? accent : dim));
    }

    /** Shows the current volume and brightness, unless a finger is on a slider. */
    private void syncSliders() {
        if (slidersBusy || slVolume == null) return;
        int vol = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        slVolume.setProgress(vol);
        int bp = brightnessPercent();
        slBrightness.setProgress(bp);
        updateSliderButtons(vol == 0, bp <= 0);
    }

    @SuppressWarnings("deprecation")
    private void setBacklight(int value) {
        boolean canWrite = android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.System.canWrite(this);
        if (canWrite) {
            try {
                android.provider.Settings.System.putInt(getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                android.provider.Settings.System.putInt(getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS, value);
                return;
            } catch (SecurityException ignored) { }
        }
        // Without permission to change the system setting, dim only this screen.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = value / 255f;
        getWindow().setAttributes(lp);
    }

    private void applyKeepAwake() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        boolean plugged = battery != null && battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
        if (plugged) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter plug = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        plug.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(plugReceiver, plug);
        applyKeepAwake();
        registerReceiver(volumeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        syncSliders();
        ObdService.sync(this);
        SwitchOverlayService.sync(this);
        DimService.sync(this);
        TripService.sync(this);
        ObdManager.get().addListener(this);
        startGps();
        connectMedia();
        statSpeedLabel.setText(prefs.speedUnit());
        if (prefs.mph()) speedo.setRange(160, 20, "mph");
        else speedo.setRange(240, 20, "km/h");
        if (prefs.fahrenheit()) gCoolant.setRange(100, 266, 221, "°F");
        else gCoolant.setRange(40, 130, 105, "°C");
        fuel.refresh();
        connect.start();
        ui.post(aaPoll);
        render();
    }

    @Override
    protected void onStop() {
        ObdManager.get().removeListener(this);
        connect.stop();
        try { unregisterReceiver(plugReceiver); } catch (IllegalArgumentException ignored) { }
        try { unregisterReceiver(volumeReceiver); } catch (IllegalArgumentException ignored) { }
        ui.removeCallbacks(aaPoll);
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
        // As a home screen the only "back" is from the gauges page to the first page.
        if (pager.page() != 0) pager.goTo(0);
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
        obdConnected = state == ObdManager.State.CONNECTED;
        showObdState(obdDot, obdStatus, state, message);
        render();
    }

    static void showObdState(ImageView dot, TextView label, ObdManager.State state, String message) {
        Context ctx = dot.getContext();
        int c;
        switch (state) {
            case CONNECTED: c = GaugeView.color(ctx, R.color.ok); break;
            case CONNECTING: c = 0xFFFFC857; break;
            default: c = GaugeView.color(ctx, R.color.text_dim);
        }
        dot.setColorFilter(c);
        label.setText(message);
    }

    private void render() {
        // OBD speed is more accurate; use GPS until the adapter is connected (or if it reports none).
        double speed = obdConnected && !Double.isNaN(lastObd.speedKmh) ? lastObd.speedKmh : gpsSpeedKmh;
        double shownSpeed = Double.isNaN(speed) ? speed : prefs.speed(speed);
        statSpeed.setText(fmt(shownSpeed, 0));
        speedo.setValue(shownSpeed);
        // The engine readings show "--" until the OBD-II adapter is connected.
        ObdData d = obdConnected ? lastObd : new ObdData();
        gRpm.setValue(d.rpm);
        gCoolant.setValue(Double.isNaN(d.coolantC) ? d.coolantC : prefs.temp(d.coolantC));
        gThrottle.setValue(d.throttlePct);
        gLoad.setValue(d.loadPct);
        gVolt.setValue(d.voltage);
        if (!obdConnected) {
            statRpm.setText("--");
            statCoolant.setText("--");
            statVolt.setText("--");
            return;
        }
        statRpm.setText(fmt(lastObd.rpm, 0));
        statCoolant.setText(Double.isNaN(lastObd.coolantC) ? "--"
                : fmt(prefs.temp(lastObd.coolantC), 0) + prefs.tempUnit());
        statVolt.setText(Double.isNaN(lastObd.voltage) ? "--" : fmt(lastObd.voltage, 1) + "V");
    }

    // ---------------------------------------------------------------- fault codes

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

    /**
     * Prefers whatever is playing, then a session that has a real track title. The idle
     * "Android Auto, Connected" placeholder from the embedded head unit only wins as a last resort.
     */
    private void pickController(List<MediaController> list) {
        MediaController pick = null;
        int best = -1;
        if (list != null) {
            for (MediaController mc : list) {
                PlaybackState s = mc.getPlaybackState();
                MediaMetadata md = mc.getMetadata();
                String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
                boolean placeholder = "Android Auto".equals(title);
                int score = 0;
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) score += 4;
                if (title != null && !title.isEmpty() && !placeholder) score += 2;
                if (s != null && s.getState() != PlaybackState.STATE_NONE) score += 1;
                if (score > best) { best = score; pick = mc; }
            }
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
        // The embedded head unit's idle session reads "Android Auto / Connected"; that is not a track.
        if (md != null && "Android Auto".equals(md.getString(MediaMetadata.METADATA_KEY_TITLE))) md = null;
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

    /** Today's and this month's top speed, and today's distance, from the drive recorder. */
    private void updateDriveStats() {
        DriveLog log = DriveLog.get(this);
        java.util.Calendar now = java.util.Calendar.getInstance();
        DriveLog.Day today = log.today(now.getTimeInMillis());
        double topDay = today == null ? 0 : today.topKmh;
        double topMonth = log.monthTop(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH));
        double kmDay = today == null ? 0 : today.km;
        sTopDay.setText(topDay > 0 ? String.format(java.util.Locale.getDefault(), "%.0f %s", prefs.speed(topDay), prefs.speedUnit()) : "--");
        sTopMonth.setText(topMonth > 0 ? String.format(java.util.Locale.getDefault(), "%.0f %s", prefs.speed(topMonth), prefs.speedUnit()) : "--");
        sKmDay.setText(String.format(java.util.Locale.getDefault(), "%.1f %s", prefs.dist(kmDay), prefs.distUnit()));
        sKmDayLabel.setText("Driven today");
    }

    /** Outlines the Android Auto tile in teal while a phone is connected. */
    private void updateAaTile() {
        boolean on = com.andrerinas.openheadunit.host.HostBridge.isConnected(this);
        if (aaShown != null && aaShown == on) return;
        aaShown = on;
        aaTile.setBackgroundResource(on ? R.drawable.tile_bg_active : R.drawable.tile_bg);
        aaIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                GaugeView.color(this, on ? R.color.accent : R.color.text)));
        aaStatus.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void openAndroidAuto() {
        startActivity(new Intent(this, com.andrerinas.openheadunit.main.MainActivity.class));
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

package com.sulthan.cardash;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.lang.ref.WeakReference;

/**
 * Full-screen player for a video cast to the tablet by {@link CastService}. The phone controls it
 * (play, pause, seek, stop); Back closes it.
 */
public class CastPlayerActivity extends Activity {
    /** UPnP transport state, read by the service when the phone asks. */
    static volatile String state = "NO_MEDIA_PRESENT";
    static volatile int posMs;
    static volatile int durMs;
    private static WeakReference<CastPlayerActivity> current = new WeakReference<>(null);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private VideoView video;
    private TextView note;
    private String uri = "";
    private boolean wantPlay;
    private boolean prepared;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (prepared) {
                posMs = video.getCurrentPosition();
                durMs = video.getDuration();
            }
            ui.postDelayed(this, 500);
        }
    };

    /** Called from the service thread; starts the screen if a video needs it. */
    static void command(final Context ctx, final String cmd, final String uri, final int seekMs) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                CastPlayerActivity a = current.get();
                if (a == null) {
                    if (cmd.equals("stop") || cmd.equals("pause") || cmd.equals("seek") || uri.isEmpty()) return;
                    Intent i = new Intent(ctx, CastPlayerActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra("cmd", cmd).putExtra("uri", uri);
                    ctx.startActivity(i);
                } else {
                    a.apply(cmd, uri, seekMs);
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        note = new TextView(this);
        note.setTextColor(0xFFFFFFFF);
        note.setTextSize(22);
        note.setGravity(Gravity.CENTER);
        note.setVisibility(View.GONE);
        root.addView(note, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                prepared = true;
                durMs = mp.getDuration();
                if (wantPlay) { video.start(); state = "PLAYING"; } else state = "PAUSED_PLAYBACK";
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) { state = "STOPPED"; posMs = 0; }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                state = "STOPPED";
                note.setText("This tablet can't play that video");
                note.setVisibility(View.VISIBLE);
                return true;
            }
        });
        current = new WeakReference<>(this);
        handleIntent(getIntent());
        ui.post(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        apply(i.getStringExtra("cmd"), i.getStringExtra("uri"), 0);
    }

    private void apply(String cmd, String newUri, int seekMs) {
        if (cmd == null) return;
        if (newUri != null && !newUri.isEmpty() && !newUri.equals(uri)) {
            uri = newUri;
            prepared = false;
            wantPlay = false;
            posMs = 0;
            note.setVisibility(View.GONE);
            state = "TRANSITIONING";
            video.setVideoURI(Uri.parse(uri));
        }
        switch (cmd) {
            case "play":
                wantPlay = true;
                if (prepared) { video.start(); state = "PLAYING"; }
                break;
            case "pause":
                wantPlay = false;
                if (prepared) { video.pause(); state = "PAUSED_PLAYBACK"; }
                break;
            case "seek":
                if (prepared) video.seekTo(seekMs);
                break;
            case "stop":
                finish();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(tick);
        if (current.get() == this) current.clear();
        video.stopPlayback();
        state = "NO_MEDIA_PRESENT";
        posMs = 0;
        durMs = 0;
        super.onDestroy();
    }
}

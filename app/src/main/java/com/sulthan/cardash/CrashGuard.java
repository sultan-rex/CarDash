package com.sulthan.cardash;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.andrerinas.openheadunit.aap.AapService;
import com.andrerinas.openheadunit.host.HostBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Catches a crash anywhere in the app (CarDash or the built-in Android Auto), writes it to a crash
 * log, and brings the app back a few seconds later. If Android Auto was connected when it crashed,
 * the connection is started again the same way a phone's Bluetooth connection starts it.
 *
 * It is a content provider only because Android creates those before anything else in the app,
 * so the crash catcher is in place from the very start.
 */
public class CrashGuard extends ContentProvider {
    static final String EXTRA_RECOVER_AA = "recover_android_auto";
    private static final String LOG_NAME = "crash.log";
    private static final long LOG_MAX = 64 * 1024;
    private static final long RESTART_DELAY_MS = 2500;
    /** More crashes than this within the window and Android Auto is left alone (a crash loop). */
    private static final int MAX_RESTARTS = 3;
    private static final long WINDOW_MS = 10 * 60_000;
    private static final String PREF_TIMES = "crash_times";

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        if (ctx == null) return false;
        final Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            boolean restarting = false;
            try {
                restarting = handle(app, thread, error);
            } catch (Throwable ignored) {
                // Never let the crash catcher itself get in the way of the normal crash.
            }
            if (restarting) {
                // End quietly: Android's "has stopped" box would sit on top of the restarted app
                // until someone taps OK, which nobody can do safely while driving.
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
            if (previous != null) previous.uncaughtException(thread, error);
            else System.exit(10);
        });
        return true;
    }

    /** Logs the crash; returns true when a restart has been scheduled. */
    private static boolean handle(Context app, Thread thread, Throwable error) {
        boolean aa = false;
        try { aa = HostBridge.isConnected(app); } catch (Throwable ignored) { }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + "  thread " + thread.getName() + (aa ? "  (Android Auto was connected)" : ""));
        error.printStackTrace(pw);
        pw.flush();
        appendLog(app, sw.toString());

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(app);
        long now = System.currentTimeMillis();
        String times = recentTimes(sp.getString(PREF_TIMES, ""), now) + now + ",";
        sp.edit().putString(PREF_TIMES, times).commit(); // commit: the process is about to die

        int recent = times.split(",").length;
        if (recent > MAX_RESTARTS) return false; // a crash loop: show Android's usual box instead

        Intent i = new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_RECOVER_AA, aa);
        int flags = PendingIntent.FLAG_CANCEL_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(app, 7, i, flags);
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + RESTART_DELAY_MS, pi);
        return true;
    }

    /** Keeps only the crash times inside the window. */
    private static String recentTimes(String csv, long now) {
        StringBuilder sb = new StringBuilder();
        for (String t : csv.split(",")) {
            try {
                if (now - Long.parseLong(t) < WINDOW_MS) sb.append(t).append(',');
            } catch (NumberFormatException ignored) { }
        }
        return sb.toString();
    }

    /** Called by MainActivity when it was opened by the restart above. */
    static void recover(Context ctx, Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_RECOVER_AA, false)) return;
        intent.removeExtra(EXTRA_RECOVER_AA); // once only, not again on rotation
        if (HostBridge.isConnected(ctx)) return;
        Intent svc = new Intent(ctx, AapService.class).setAction(AapService.ACTION_BT_AUTO_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
            else ctx.startService(svc);
        } catch (RuntimeException ignored) { }
        try {
            ctx.startActivity(new Intent(ctx, com.andrerinas.openheadunit.main.MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(com.andrerinas.openheadunit.main.MainActivity.EXTRA_LAUNCH_SOURCE,
                            com.andrerinas.openheadunit.main.MainActivity.LAUNCH_SOURCE_BLUETOOTH));
        } catch (RuntimeException ignored) { }
    }

    static File logFile(Context ctx) {
        return new File(ctx.getFilesDir(), LOG_NAME);
    }

    /** The crash log text, newest last, or null when there has been no crash. */
    static String readLog(Context ctx) {
        File f = logFile(ctx);
        if (!f.isFile()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new String(b, 0, n, "UTF-8");
        } catch (IOException e) {
            return null;
        }
    }

    static void clearLog(Context ctx) {
        //noinspection ResultOfMethodCallIgnored
        logFile(ctx).delete();
        //noinspection ResultOfMethodCallIgnored
        new File(Backup.folder(), LOG_NAME).delete();
    }

    private static void appendLog(Context ctx, String entry) {
        File f = logFile(ctx);
        String old = f.length() > LOG_MAX ? "" : readOrEmpty(ctx);
        String text = old + entry + "\n";
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes("UTF-8"));
        } catch (IOException ignored) { }
        // A copy where the Mac can read it over USB without debug access.
        try { Backup.write(new File(Backup.folder(), LOG_NAME), text); } catch (IOException ignored) { }
    }

    private static String readOrEmpty(Context ctx) {
        String s = readLog(ctx);
        return s == null ? "" : s;
    }

    // A content provider with no content.
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}

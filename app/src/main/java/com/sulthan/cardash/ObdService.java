package com.sulthan.cardash;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps the OBD connection alive while other apps (Android Auto, maps) are in front. */
public class ObdService extends Service {
    private static final String CHANNEL = "obd";
    private static final int NOTIFICATION_ID = 1;

    /** Starts or stops the service to match the current settings. */
    public static void sync(Context ctx) {
        Intent i = new Intent(ctx, ObdService.class);
        if (new Prefs(ctx).wantsObd()) {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } else {
            ctx.stopService(i);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Prefs prefs = new Prefs(this);
        if (!prefs.wantsObd()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();
        ObdManager.get().start(prefs.obdDevice());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ObdManager.get().stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startInForeground() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "OBD connection",
                    NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = b.setSmallIcon(R.drawable.ic_speed)
                .setContentTitle("CarDash")
                .setContentText("Reading engine data")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }
}

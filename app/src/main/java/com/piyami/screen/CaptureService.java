package com.piyami.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

public class CaptureService extends Service {
    private final IBinder binder = new Binder();

    @Override public void onCreate() {
        super.onCreate();
        String channel = "capture";
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(channel, "Ekran görüntüsü", NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(this, channel);
        } else {
            builder = new Notification.Builder(this);
        }
        startForeground(1, builder.setContentTitle("Screen")
                .setContentText("Ekran görüntüsü hazırlanıyor")
                .setSmallIcon(android.R.drawable.ic_menu_camera).build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { return binder; }
}

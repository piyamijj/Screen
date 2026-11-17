package com.piyami.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;

final class LegacyApi26 {
    static void startForegroundService(Context context, Intent intent) {
        context.startForegroundService(intent);
    }

    static Notification.Builder notificationBuilder(Context context) {
        String channel = "capture";
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(channel, "Ekran görüntüsü", NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(context, channel);
    }

    static int overlayType() {
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }
}

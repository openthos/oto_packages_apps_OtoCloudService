package org.openthos.seafile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public abstract class BaseService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel("id", "name", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
        Notification.Builder builder = new Notification.Builder(this, "id");
        builder.setSmallIcon(R.drawable.ic_launcher);
        Notification notification = builder.build();
        startForeground(1, notification);
    }

    public static void startService(Context context, Intent intent) {
        context.startForegroundService(intent);
    }
}

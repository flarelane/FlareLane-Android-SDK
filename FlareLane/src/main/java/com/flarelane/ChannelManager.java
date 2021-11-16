package com.flarelane;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

class ChannelManager {
    protected static String getChannelId(Context context) {
        return context.getString(R.string.default_notification_channel_id);
    }

    protected static String getChannelName(Context context) {
        return context.getString(R.string.default_notification_channel_name);
    }

    protected static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(getChannelId(context), getChannelName(context), NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(false);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}

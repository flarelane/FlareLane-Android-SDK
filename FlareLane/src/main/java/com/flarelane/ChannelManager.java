package com.flarelane;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

class ChannelManager {
    private static final String DEFAULT_CHANNEL_ID = "com.flarelane.default_notification_channel_id";

    protected static String getDefaultChannelId() {
        return DEFAULT_CHANNEL_ID;
    }

    protected static String getChannelName(Context context) {
        return context.getString(R.string.default_notification_channel_name);
    }

    protected static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(getDefaultChannelId(), getChannelName(context), NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(false);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}

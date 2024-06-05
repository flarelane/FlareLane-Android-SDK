package com.flarelane;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.flarelane.util.AndroidUtils;

class ChannelManager {
    private static final String DEFAULT_CHANNEL_ID = "com.flarelane.default_notification_channel_id";

    protected static String getDefaultChannelId(Context context) {
        String customChannelId = getCustomChannelId(context);
        return customChannelId != null ? customChannelId : DEFAULT_CHANNEL_ID;
    }

    protected static String getCustomChannelId(Context context) {
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return appInfo.metaData.getString(Constants.DEFAULT_CHANNEL_ID);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    protected static String getDefaultChannelName(Context context) {
        String customChannelName = Helper.getResourceString(context.getApplicationContext(), Constants.DEFAULT_CHANNEL_NAME);
        return customChannelName != null ? customChannelName : context.getString(R.string.default_notification_channel_name);
    }

    protected static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If custom channel id exists, not create a notification channel by FlareLane.
            if (getCustomChannelId(context) != null) {
                return;
            }

            NotificationChannel channel = new NotificationChannel(getDefaultChannelId(context), getDefaultChannelName(context), NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }
}

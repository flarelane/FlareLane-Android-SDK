package com.flarelane;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.legacy.content.WakefulBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Random;
import java.util.Set;

public class FCMBroadcastReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    showNotification(context, intent);
                } catch (Exception e) {
                    BaseErrorHandler.handle(e);
                }
            }
        }).start();
    }

    private void showNotification(Context context, Intent intent) throws Exception {
        if (intent == null || intent.getExtras() == null) {
            Logger.error("intent is NULL");
            return;
        }

        JSONObject jsonObject = bundleAsJSONObject(intent.getExtras());

        if (jsonObject == null) {
            Logger.error("jsonObject is NULL");
            return;
        }

        String isFlareLane = jsonObject.optString("isFlareLane");
        if (isFlareLane == null || !isFlareLane.contentEquals("true")) {
            Logger.verbose("It is not a message of FlareLane");
            return;
        }

        Notification flarelaneNotification = new Notification(jsonObject);

        com.flarelane.Logger.verbose("Message data payload: " + flarelaneNotification.toString());

        String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);
        String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, false);

        boolean isForeground = (Helper.appInForeground(context));
        com.flarelane.Logger.verbose("onMessageReceived isForeground: " + isForeground);
        if (isForeground) {
            EventService.createForegroundReceived(projectId, deviceId, flarelaneNotification);
        } else {
            EventService.createBackgroundReceived(projectId, deviceId, flarelaneNotification);
        }

        Intent convertedIntent = new Intent(context, NotificationConvertedActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("title", flarelaneNotification.title)
                .putExtra("body", flarelaneNotification.body)
                .putExtra("url", flarelaneNotification.url)
                .putExtra("imageUrl", flarelaneNotification.imageUrl)
                .putExtra("data", flarelaneNotification.data)
                .putExtra("notificationId", flarelaneNotification.id);
        PendingIntent contentIntent = PendingIntent.getActivity(context, new Random().nextInt(543254), convertedIntent, PendingIntent.FLAG_IMMUTABLE);

        int currentIcon = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA).icon;

        Bitmap image = null;
        if (flarelaneNotification.imageUrl != null) {
            try {
                URL url = new URL(flarelaneNotification.imageUrl);
                InputStream in;
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                in = connection.getInputStream();
                image = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Logger.error(Log.getStackTraceString(e));
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ChannelManager.getDefaultChannelId())
                .setSmallIcon(getNotificationIcon(context))
                .setContentText(flarelaneNotification.body)
                .setContentTitle(flarelaneNotification.title == null ? context.getApplicationInfo().loadLabel(context.getPackageManager()).toString() : flarelaneNotification.title)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        try {
            String accentColor = Helper.getResourceString(context.getApplicationContext(), "flarelane_notification_accent_color");
            if (accentColor != null) {
                builder = builder.setColor(Color.parseColor(accentColor));
            }
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }

        if (image != null) {
            builder = builder
                    .setLargeIcon(image)
                    .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(image).bigLargeIcon(null).setSummaryText(flarelaneNotification.body));
        } else {
            builder = builder.setStyle(new NotificationCompat.BigTextStyle().bigText(flarelaneNotification.body));
        }

        android.app.Notification notification = builder.build();

        notification.defaults|= android.app.Notification.DEFAULT_SOUND;
        notification.defaults|= android.app.Notification.DEFAULT_LIGHTS;
        notification.defaults|= android.app.Notification.DEFAULT_VIBRATE;

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) new Date().getTime(), notification);
    }

    private int getNotificationIcon(Context context) {
        try {
            // TODO: Temporarily available only from Lollipop higher
            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                // TODO: if notificationIcon was set (LEGACY)
                if (FlareLane.notificationIcon != 0) {
                    return FlareLane.notificationIcon;
                }

                // if default notification icon is exists
                String defaultIconIdentifierName = "ic_stat_flarelane_default";
                int getDefaultIconId = context.getResources().getIdentifier(defaultIconIdentifierName, "drawable", context.getPackageName());
                if (getDefaultIconId != 0) {
                    return getDefaultIconId;
                }
            }
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }

        // Use a system default notification icon
        return android.R.drawable.ic_menu_info_details;
    }

    private JSONObject bundleAsJSONObject(Bundle bundle) {
        JSONObject json = new JSONObject();
        Set<String> keys = bundle.keySet();

        for (String key : keys) {
            try {
                json.put(key, bundle.get(key));
            } catch (JSONException e) {
                Logger.error(Log.getStackTraceString(e));
            }
        }

        return json;
    }
}

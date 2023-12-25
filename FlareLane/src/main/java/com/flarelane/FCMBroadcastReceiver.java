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

        JSONObject data = new JSONObject(flarelaneNotification.data);
        String dismissForegroundNotificationKey = "flarelane_dismiss_foreground_notification";
        boolean dismissForegroundNotification = data.has(dismissForegroundNotificationKey) ? data.getString(dismissForegroundNotificationKey).contentEquals("true") : false;
        if (isForeground && dismissForegroundNotification) {
            Logger.verbose("notification dismissed cause flarelane_dismiss_foreground_notification is true.");
            return;
        }

        if (isForeground) {
            EventService.createForegroundReceived(projectId, deviceId, flarelaneNotification);
        } else {
            EventService.createBackgroundReceived(projectId, deviceId, flarelaneNotification);
        }

        NotificationReceivedEvent event = new NotificationReceivedEvent(context.getApplicationContext(), flarelaneNotification);

        if (isForeground && FlareLane.notificationForegroundReceivedHandler != null) {
            Logger.verbose("notificationForegroundReceivedHandler exists, you can control the display timing.");
            FlareLane.notificationForegroundReceivedHandler.onWillDisplay(event);
            return;
        }

        event.display();
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

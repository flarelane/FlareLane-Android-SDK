package com.flarelane;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.legacy.content.WakefulBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;

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

        String isFlareLane = jsonObject.optString("isFlareLane");
        if (!isFlareLane.contentEquals("true")) {
            Logger.verbose("It is not a message of FlareLane");
            return;
        }

        Notification flarelaneNotification = new Notification(jsonObject);
        com.flarelane.Logger.verbose("Message data payload: " + flarelaneNotification);

        boolean isForeground = Helper.appInForeground(context);
        com.flarelane.Logger.verbose("onMessageReceived isForeground: " + isForeground);

        JSONObject data = flarelaneNotification.getDataJsonObject();
        boolean dismissForegroundNotification = data != null &&
                data.has(Constants.REMOTE_DISMISS_FOREGROUND_NOTIFICATION) &&
                data.getBoolean(Constants.REMOTE_DISMISS_FOREGROUND_NOTIFICATION);
        if (isForeground && dismissForegroundNotification) {
            Logger.verbose("notification dismissed cause " + Constants.REMOTE_DISMISS_FOREGROUND_NOTIFICATION + " is true.");
            return;
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

package com.flarelane;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.legacy.content.WakefulBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
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
            Logger.error("Notification", "intent is null");
            return;
        }

        JSONObject jsonObject = bundleAsJSONObject(intent.getExtras());

        String isFlareLane = jsonObject.optString("isFlareLane");
        if (!isFlareLane.contentEquals("true")) {
            Logger.verbose("Notification", "not a FlareLane message");
            return;
        }

        Notification flarelaneNotification = new Notification(jsonObject);
        com.flarelane.Logger.verbose("Notification", "message payload received", Collections.singletonMap("notification", flarelaneNotification));

        boolean isForeground = Helper.appInForeground(context);
        com.flarelane.Logger.verbose("Notification", "onMessageReceived", Collections.singletonMap("isForeground", isForeground));

        JSONObject data = flarelaneNotification.getDataJsonObject();
        boolean dismissForegroundNotification = data != null &&
                data.optString(Constants.DISMISS_FOREGROUND_NOTIFICATION).equals("true");
        if (isForeground && dismissForegroundNotification) {
            Logger.info("Notification", "dismissed by dismissForegroundNotification flag", Collections.singletonMap("flag", Constants.DISMISS_FOREGROUND_NOTIFICATION));
            return;
        }

        NotificationReceivedEvent event = new NotificationReceivedEvent(context.getApplicationContext(), flarelaneNotification);

        if (isForeground && FlareLane.notificationForegroundReceivedHandler != null) {
            Logger.info("Notification", "foreground received handler will control display timing");
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
                Logger.error("Notification", "bundle to json failed", Collections.singletonMap("stackTrace", Log.getStackTraceString(e)));
            }
        }

        return json;
    }
}

package com.flarelane.example;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.flarelane.FlareLane;
import com.flarelane.InAppMessage;
import com.flarelane.InAppMessageActionHandler;
import com.flarelane.Notification;
import com.flarelane.NotificationClickedHandler;
import com.flarelane.NotificationForegroundReceivedHandler;
import com.flarelane.NotificationReceivedEvent;

import org.json.JSONObject;

public class MainApplication extends Application {
    private static final String FLARELANE_PROJECT_ID = "FLARELANE_PROJECT_ID";

    @Override
    public void onCreate() {
        super.onCreate();

        FlareLane.setLogLevel(Log.VERBOSE);
        FlareLane.initWithContext(this, FLARELANE_PROJECT_ID, false);
        FlareLane.setNotificationClickedHandler(new NotificationClickedHandler() {
            @Override
            public void onClicked(Notification notification) {
                Log.d("FlareLane", "NotificationClickedHandler.onClicked: " + notification.toString());
            }
        });

        FlareLane.setNotificationForegroundReceivedHandler((new NotificationForegroundReceivedHandler() {
            @Override
            public void onWillDisplay(NotificationReceivedEvent notificationReceivedEvent) {
                Notification notification = notificationReceivedEvent.getNotification();
                Log.d("FlareLane", "NotificationForegroundReceivedHandler.onWillDisplay: " + notification.toString());

                try {
                    JSONObject data = new JSONObject(notification.data);
                    String dismissForegroundNotificationKey = "dismiss_foreground_notification";
                    boolean dismissForegroundNotification = data.has(dismissForegroundNotificationKey) ? data.getString(dismissForegroundNotificationKey).contentEquals("true") : false;
                    if (dismissForegroundNotification) return;

                    notificationReceivedEvent.display();
                } catch (Exception e) {}
            }
        }));

        FlareLane.setInAppMessageActionHandler(new InAppMessageActionHandler() {
            @Override
            public void onExecute(@NonNull InAppMessage iam, @NonNull String actionId) {
                Log.d("FlareLane", "setInAppMessageActionHandler.onExecute: " + iam.toString() + " actionId: " + actionId);
            }
        });


    }

}

package com.flarelane.example;

import android.app.Application;
import android.util.Log;

import com.flarelane.FlareLane;
import com.flarelane.Notification;
import com.flarelane.NotificationConvertedHandler;
import com.flarelane.NotificationManager;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        FlareLane.setLogLevel(Log.VERBOSE);
        FlareLane.initWithContext(this, "INPUT_YOUR_PROJECT_ID");
        NotificationManager.accentColor = "#1D4289";
        FlareLane.setNotificationConvertedHandler(new NotificationConvertedHandler() {
            @Override
            public void onConverted(Notification notification) {
                Log.d("FlareLane-Example", "onConverted: " + notification.toString());
            }
        });
    }
}

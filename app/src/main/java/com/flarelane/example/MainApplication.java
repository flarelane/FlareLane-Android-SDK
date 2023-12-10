package com.flarelane.example;

import android.app.Application;
import android.util.Log;

import com.flarelane.FlareLane;
import com.flarelane.Notification;
import com.flarelane.NotificationConvertedHandler;

public class MainApplication extends Application {
    private static final String FLARELANE_PROJECT_ID = "FLARELANE_PROJECT_ID";

    @Override
    public void onCreate() {
        super.onCreate();

        FlareLane.setLogLevel(Log.VERBOSE);
        FlareLane.initWithContext(this, FLARELANE_PROJECT_ID, true);
        FlareLane.setNotificationConvertedHandler(new NotificationConvertedHandler() {
            @Override
            public void onConverted(Notification notification) {
                Log.d("FlareLane", "onConverted: " + notification.toString());
            }
        });
    }

}

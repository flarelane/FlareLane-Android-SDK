package com.flarelane.example;

import android.app.Application;
import android.util.Log;

import com.flarelane.FlareLane;
import com.flarelane.Notification;
import com.flarelane.NotificationConvertedHandler;
import com.flarelane.NotificationManager;


public class MainApplication extends Application {
    private static final String FLARELANE_PROJECT_ID = "FLARELANE_PROJECT_ID";
    private static final String ONESIGNAL_APP_ID = "ONESIGNAL_APP_ID";

    @Override
    public void onCreate() {
        super.onCreate();

        FlareLane.setLogLevel(Log.VERBOSE);
        FlareLane.initWithContext(this, FLARELANE_PROJECT_ID, true);
        NotificationManager.accentColor = "#1D4289";
        FlareLane.setNotificationConvertedHandler(new NotificationConvertedHandler() {
            @Override
            public void onConverted(Notification notification) {
                Log.d("FlareLane", "onConverted: " + notification.toString());
            }
        });

//        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
//        OneSignal.initWithContext(this);
//        OneSignal.setAppId(ONESIGNAL_APP_ID);
//        OneSignal.promptForPushNotifications();
//        OneSignal.setNotificationOpenedHandler(new OneSignal.OSNotificationOpenedHandler() {
//            @Override
//            public void notificationOpened(OSNotificationOpenedResult result) {
//                Log.d("debug", "asd");
//            }
//        });

//        FlareLane.subscribe(this, true, new FlareLane.IsSubscribedHandler() {
//            @Override
//            public void onSuccess(boolean isSubscribed) {
//
//            }
//        });
    }

}

package com.flarelane.example;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.flarelane.FlareLane;
import com.flarelane.Notification;
import com.flarelane.NotificationConvertedHandler;
import com.flarelane.NotificationManager;
import com.onesignal.OneSignal;

public class MainApplication extends Application {
    private static final String FLARELANE_PROJECT_ID = "FLARELANE_PROJECT_ID";
    private static final String ONESIGNAL_APP_ID = "ONESIGNAL_APP_ID";

    @Override
    public void onCreate() {
        super.onCreate();

        FlareLane.setLogLevel(Log.VERBOSE);
        FlareLane.initWithContext(this, FLARELANE_PROJECT_ID);
        NotificationManager.accentColor = "#1D4289";
        FlareLane.setNotificationConvertedHandler(new NotificationConvertedHandler() {
            @Override
            public void onConverted(Notification notification) {
                Log.d("FlareLane", "onConverted: " + notification.toString());
            }
        });

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        OneSignal.initWithContext(this);
        OneSignal.setAppId(ONESIGNAL_APP_ID);
        OneSignal.promptForPushNotifications();
    }

}

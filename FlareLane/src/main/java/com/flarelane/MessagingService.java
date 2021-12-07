package com.flarelane;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Date;
import java.util.List;
import java.util.Random;

public class MessagingService extends FirebaseMessagingService {
    private static boolean appInForeground(@NonNull Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = activityManager.getRunningAppProcesses();
        if (runningAppProcesses == null) {
            return false;
        }

        for (ActivityManager.RunningAppProcessInfo runningAppProcess : runningAppProcesses) {
            if (runningAppProcess.processName.equals(context.getPackageName()) &&
                    runningAppProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onNewToken(String token) {
        com.flarelane.Logger.verbose("onNewToken");
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        try {
            super.onMessageReceived(remoteMessage);

            if (remoteMessage == null)
                return;

            boolean isForeground = (appInForeground(this.getApplicationContext()));
            com.flarelane.Logger.verbose("onMessageReceived isForeground: " + isForeground);

            if (remoteMessage.getData().size() > 0) {
                com.flarelane.Logger.verbose("Message data payload: " + remoteMessage.getData());

                String isFlareLane = remoteMessage.getData().get("isFlareLane");
                if (isFlareLane == null || !isFlareLane.contentEquals("true")) {
                    Logger.verbose("It is not a message of FlareLane");
                    return;
                }

                com.flarelane.Notification flarelaneNotification = new com.flarelane.Notification(
                        remoteMessage.getData().get("notificationId"),
                        remoteMessage.getData().get("body"),
                        remoteMessage.getData().get("title"),
                        remoteMessage.getData().get("url")
                );

                String projectId = com.flarelane.BaseSharedPreferences.getProjectId(this.getApplicationContext(), false);
                String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(this.getApplicationContext(), false);

                if (isForeground) {
                    EventService.createForegroundReceived(projectId, deviceId, flarelaneNotification);
                } else {
                    EventService.createBackgroundReceived(projectId, deviceId, flarelaneNotification);
                }

                Intent intent = new Intent(this.getApplicationContext(), NotificationConvertedActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("title", flarelaneNotification.title)
                        .putExtra("body", flarelaneNotification.body)
                        .putExtra("url", flarelaneNotification.url)
                        .putExtra("notificationId", flarelaneNotification.id);
                PendingIntent contentIntent = PendingIntent.getActivity(this.getApplicationContext(), new Random().nextInt(543254), intent, PendingIntent.FLAG_UPDATE_CURRENT);

                int currentIcon = getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA).icon;
                Context context = this.getApplicationContext();

                Notification notification = new NotificationCompat.Builder(this.getApplicationContext(), ChannelManager.getChannelId(this.getApplicationContext()))
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .setContentText(flarelaneNotification.body)
                        .setContentTitle(flarelaneNotification.title == null ? context.getApplicationInfo().loadLabel(context.getPackageManager()).toString() : flarelaneNotification.title)
                        .setAutoCancel(true)
                        .setContentIntent(contentIntent)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .build();

                notification.defaults|= Notification.DEFAULT_SOUND;
                notification.defaults|= Notification.DEFAULT_LIGHTS;
                notification.defaults|= Notification.DEFAULT_VIBRATE;

                NotificationManagerCompat.from(this.getApplicationContext()).notify((int) new Date().getTime(), notification);
            }
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
        }
    }
}

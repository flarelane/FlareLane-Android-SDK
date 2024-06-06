package com.flarelane;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.flarelane.util.ExtensionsKt;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Random;

public class NotificationReceivedEvent {
    private Context context;
    private Notification notification;

    public NotificationReceivedEvent(Context context, Notification notification) {
        this.context = context;
        this.notification = notification;
    }

    public Notification getNotification() {
        return notification;
    }

    public void display() {
        try {
            Notification flarelaneNotification = this.getNotification();
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);
            String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, false);

            boolean isForeground = (Helper.appInForeground(context));

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent clickedIntent = new Intent(context, NotificationClickedActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        ExtensionsKt.putParcelableDataClass(clickedIntent, notification);

                        PendingIntent contentIntent = PendingIntent.getActivity(context, new Random().nextInt(543254), clickedIntent, PendingIntent.FLAG_IMMUTABLE);

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
                                BaseErrorHandler.handle(e);
                            }
                        }

                        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, flarelaneNotification.currentChannelId(context))
                                .setSmallIcon(getNotificationIcon(context))
                                .setContentText(flarelaneNotification.body)
                                .setContentTitle(flarelaneNotification.title == null ? context.getApplicationInfo().loadLabel(context.getPackageManager()).toString() : flarelaneNotification.title)
                                .setAutoCancel(true)
                                .setContentIntent(contentIntent)
                                .setPriority(NotificationCompat.PRIORITY_MAX)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

                        try {
                            String accentColor = Helper.getResourceString(context.getApplicationContext(), Constants.NOTIFICATION_ACCENT_COLOR);
                            if (accentColor != null) {
                                builder = builder.setColor(Color.parseColor(accentColor));
                            }
                        } catch (Exception e) {
                            BaseErrorHandler.handle(e);
                        }

                        if (image != null) {
                            builder = builder
                                    .setLargeIcon(image)
                                    .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(image).bigLargeIcon(null).setSummaryText(flarelaneNotification.body));
                        } else {
                            builder = builder.setStyle(new NotificationCompat.BigTextStyle().bigText(flarelaneNotification.body));
                        }

                        android.app.Notification notification = builder.build();

                        notification.defaults |= android.app.Notification.DEFAULT_SOUND;
                        notification.defaults |= android.app.Notification.DEFAULT_LIGHTS;
                        notification.defaults |= android.app.Notification.DEFAULT_VIBRATE;

                        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        notificationManager.notify(flarelaneNotification.currentAndroidNotificationId(), notification);

                        if (isForeground) {
                            EventService.createForegroundReceived(projectId, deviceId, flarelaneNotification);
                        } else {
                            EventService.createBackgroundReceived(projectId, deviceId, flarelaneNotification);
                        }
                    } catch (Exception e) {
                        BaseErrorHandler.handle(e);
                    }
                }
            }).start();
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }

    }

    @SuppressLint("DiscouragedApi")
    private int getNotificationIcon(Context context) {
        try {
            // TODO: Temporarily available only from Lollipop higher
            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                // TODO: if notificationIcon was set (LEGACY)
                if (FlareLane.notificationIcon != 0) {
                    return FlareLane.notificationIcon;
                }

                // if default notification icon is exists
                int getDefaultIconId = context.getResources().getIdentifier(Constants.ID_IC_STAT_DEFAULT, "drawable", context.getPackageName());
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

}

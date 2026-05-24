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
            String userId = com.flarelane.BaseSharedPreferences.getUserId(context, true);

            boolean isForeground = (Helper.appInForeground(context));

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Deterministic per-notification base so two concurrent notifications
                        // can't collide on requestCode and end up sharing a PendingIntent (which
                        // would deliver stale extras due to FLAG_IMMUTABLE). Body taps use the
                        // base; each action button reuses base + (index + 1) below, so the
                        // (notification id, button slot) pair is the effective unique key.
                        int baseRequestCode = flarelaneNotification.currentAndroidNotificationId();
                        PendingIntent contentIntent = buildClickedPendingIntent(context, flarelaneNotification, baseRequestCode);

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

                        // Action buttons — one NotificationCompat.Action per parsed button. Each
                        // PendingIntent carries a Notification with its own clickedButtonIndex
                        // baked in (no separate Intent extra), and a distinct requestCode so the
                        // system doesn't collapse them into a single intent.
                        java.util.List<NotificationButton> buttons = flarelaneNotification.getButtonList();
                        for (int i = 0; i < buttons.size(); i++) {
                            NotificationButton button = buttons.get(i);
                            Notification withIdx = flarelaneNotification.withClickedButtonIndex(i);
                            PendingIntent actionIntent = buildClickedPendingIntent(
                                    context, withIdx, baseRequestCode + i + 1);
                            builder.addAction(0, button.label, actionIntent);
                        }

                        android.app.Notification notification = builder.build();

                        notification.defaults |= android.app.Notification.DEFAULT_SOUND;
                        notification.defaults |= android.app.Notification.DEFAULT_LIGHTS;
                        notification.defaults |= android.app.Notification.DEFAULT_VIBRATE;

                        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        notificationManager.notify(flarelaneNotification.currentAndroidNotificationId(), notification);

                        // Idempotency guard: FCM may redeliver the same message and `event.display()`
                        // can be called multiple times by a foreground handler. We need RECEIVED
                        // events to land on the backend exactly once per (notification, lifecycle)
                        // pairing — `NotificationEventProcessor` keys on `id#eventType` so a
                        // receive followed by a click stays as two distinct events.
                        String eventType = isForeground ? EventType.ForegroundReceived : EventType.BackgroundReceived;
                        if (!NotificationEventProcessor.INSTANCE.shouldProcess(context, flarelaneNotification.id, eventType)) {
                            Logger.verbose("Notification " + eventType + " already processed, skipping: " + flarelaneNotification.id);
                        } else if (isForeground) {
                            EventService.createForegroundReceived(projectId, deviceId, flarelaneNotification, userId);
                        } else {
                            EventService.createBackgroundReceived(projectId, deviceId, flarelaneNotification, userId);
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

    /**
     * Build the PendingIntent that fires NotificationClickedActivity when the user taps the
     * notification body or one of its action buttons. The caller embeds {@code clickedButtonIndex}
     * directly on the passed {@link Notification} (via {@link Notification#withClickedButtonIndex})
     * so there is no out-of-band Intent extra carrying the index — the Parcelable is the single
     * source of truth for which button (if any) was tapped.
     *
     * <p>Caller passes a unique requestCode per PendingIntent so the system keeps them distinct
     * (otherwise the OS would collapse them into the first one).
     */
    private PendingIntent buildClickedPendingIntent(Context context, Notification flarelaneNotification, int requestCode) {
        Intent clickedIntent = new Intent(context, NotificationClickedActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ExtensionsKt.putParcelableDataClass(clickedIntent, flarelaneNotification);
        return PendingIntent.getActivity(context, requestCode, clickedIntent, PendingIntent.FLAG_IMMUTABLE);
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

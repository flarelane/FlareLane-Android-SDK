package com.flarelane;

import android.content.Context;

/**
 * Mirror of {@link NotificationReceivedEvent} for the click path: wraps a (context, notification)
 * pair and exposes {@link #process()} to run the click pipeline (CLICKED event + click handler).
 * Mirrors the same shape so the receive-vs-click symmetry is obvious to readers.
 *
 * <p>The public click handler signature passes the underlying {@link Notification} directly —
 * callers read {@code notification.getClickedButton()}, {@code notification.getClickedUrl()},
 * etc. on that. This wrapper exists so the internal processing pipeline has a single object
 * to thread through.
 *
 * <p>Deep-link / launchApp handling lives in {@link NotificationClickedActivity} because it needs
 * Activity-level APIs (startActivity, isTaskRoot). The Activity creates this event and then
 * handles the link on its own — keeping UI side-effects out of this data wrapper.
 */
public class NotificationClickedEvent {
    private final Context context;
    private final Notification notification;

    public NotificationClickedEvent(Context context, Notification notification) {
        this.context = context;
        this.notification = notification;
    }

    public Notification getNotification() {
        return notification;
    }

    /**
     * Sends the CLICKED event to the server and invokes {@code FlareLane.notificationClickedHandler}
     * (or stashes the notification as `unhandledClickedNotification` when no handler is registered yet).
     *
     * <p>Idempotent per notification: {@link NotificationEventProcessor#shouldProcess} guards entry
     * so that a single notification can never produce more than one CLICKED POST + one handler
     * invocation, even when the OS re-fires the same PendingIntent or the user re-taps quickly.
     */
    public void process() {
        try {
            if (!NotificationEventProcessor.INSTANCE.shouldProcess(context, notification.id, EventType.Clicked)) {
                Logger.verbose("Notification CLICKED already processed, skipping: " + notification.id);
                return;
            }
            String projectId = BaseSharedPreferences.getProjectId(context, false);
            String deviceId = BaseSharedPreferences.getDeviceId(context, false);
            String userId = BaseSharedPreferences.getUserId(context, true);
            // EventService is a Kotlin `object`; reach the singleton via INSTANCE from Java.
            EventService.INSTANCE.createNotificationClicked(projectId, deviceId, notification, userId);
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
        }
    }
}

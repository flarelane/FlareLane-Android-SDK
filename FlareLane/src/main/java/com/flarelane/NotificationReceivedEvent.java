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
import androidx.core.app.Person;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.flarelane.util.ExtensionsKt;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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
                            image = downloadBitmap(flarelaneNotification.imageUrl);
                        }

                        // Chat-style sender avatar. Failure keeps `avatar` null, which makes the
                        // style branch below fall back to a normal notification — an app-icon
                        // notification beats a chat bubble with a broken monogram (same policy
                        // as iOS).
                        NotificationCommunication communication = flarelaneNotification.getCommunicationData();
                        Bitmap avatar = null;
                        if (communication != null) {
                            avatar = downloadBitmap(communication.senderImageUrl);
                        }
                        boolean isConversation = communication != null && avatar != null;

                        // Conversations stack messenger-style: every push in the same conversation
                        // (threadId, falling back to per-notification id) reuses ONE stable android
                        // notification id and appends to the existing MessagingStyle history —
                        // group/summary machinery doesn't apply because OEM shades pull
                        // shortcut-backed conversations out into their own section.
                        String conversationKey = flarelaneNotification.threadId != null && !flarelaneNotification.threadId.isEmpty()
                                ? flarelaneNotification.threadId
                                : flarelaneNotification.id;
                        int conversationNotificationId = flarelaneNotification.conversationNotificationId();

                        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

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

                        // Opt-in grouping: only pushes that explicitly carry threadId are grouped
                        // (industry default — no key means the OS's own auto-bundling applies).
                        // Android requires a summary sibling for custom groups; it is refreshed
                        // after notify() below and on every dismiss/click.
                        String threadId = flarelaneNotification.threadId;
                        // Conversations opt out of group/summary: OEM shades pull shortcut-backed
                        // conversations into their own section, so the summary would orphan.
                        // Their stacking comes from the shared conversation notification id above.
                        boolean isGrouped = threadId != null && !threadId.isEmpty() && !isConversation;

                        // Concurrent FCM deliveries for the same conversation must not interleave
                        // between reading the live MessagingStyle history and re-posting it, or one
                        // delivery's message overwrites the other's. The lock is held from the
                        // history read through notify() below; non-conversation pushes skip it.
                        java.util.concurrent.locks.ReentrantLock conversationLock = null;
                        if (isConversation) {
                            conversationLock = conversationLockFor(conversationNotificationId);
                            conversationLock.lock();
                        }
                        try {

                        if (isConversation) {
                            // Conversation rendering: the sender's name replaces the title and
                            // the avatar fills BOTH the Person icon (expanded message row) and
                            // largeIcon — OEM shades (e.g. One UI) show largeIcon on the
                            // collapsed row, which is what makes it read as a chat at a glance.
                            // A big picture cannot be combined with MessagingStyle, so `imageUrl`
                            // is ignored for chat-style pushes.
                            Person sender = new Person.Builder()
                                    .setName(communication.senderName)
                                    .setIcon(IconCompat.createWithBitmap(avatar))
                                    .build();

                            // Append to the live conversation history (messenger behavior: one
                            // notification per conversation counting up, like chat apps) instead
                            // of stacking a new notification per push.
                            NotificationCompat.MessagingStyle messagingStyle = null;
                            try {
                                for (android.service.notification.StatusBarNotification sbn : notificationManager.getActiveNotifications()) {
                                    if (sbn.getId() == conversationNotificationId) {
                                        messagingStyle = NotificationCompat.MessagingStyle
                                                .extractMessagingStyleFromNotification(sbn.getNotification());
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                BaseErrorHandler.handle(e);
                            }
                            if (messagingStyle == null) {
                                messagingStyle = new NotificationCompat.MessagingStyle(sender);
                            }
                            messagingStyle.addMessage(flarelaneNotification.body, System.currentTimeMillis(), sender);

                            builder = builder
                                    .setLargeIcon(avatar)
                                    .setStyle(messagingStyle);

                            // Android 11+/OEM shades render the sender avatar as the primary icon
                            // (the messenger-app look) only for notifications tied to a published
                            // conversation shortcut. Push one per conversation; failure degrades
                            // to plain MessagingStyle, never blocks the notification.
                            try {
                                String shortcutId = "flarelane_conversation_" + conversationKey;
                                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                                if (launchIntent != null) {
                                    launchIntent.setAction(Intent.ACTION_MAIN);
                                    ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, shortcutId)
                                            .setLongLived(true)
                                            .setShortLabel(communication.senderName)
                                            .setIcon(IconCompat.createWithBitmap(avatar))
                                            .setPerson(sender)
                                            .setIntent(launchIntent)
                                            .build();
                                    ShortcutManagerCompat.pushDynamicShortcut(context, shortcut);
                                    builder = builder.setShortcutId(shortcutId);
                                }
                            } catch (Exception e) {
                                BaseErrorHandler.handle(e);
                            }
                        } else if (image != null) {
                            builder = builder
                                    .setLargeIcon(image)
                                    .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(image).bigLargeIcon(null).setSummaryText(flarelaneNotification.body));
                        } else {
                            builder = builder.setStyle(new NotificationCompat.BigTextStyle().bigText(flarelaneNotification.body));
                        }

                        if (isGrouped) {
                            builder = builder
                                    .setGroup(threadId)
                                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                                    .setDeleteIntent(NotificationDismissedReceiver.buildPendingIntent(
                                            context,
                                            threadId,
                                            flarelaneNotification.currentChannelId(context),
                                            baseRequestCode));
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

                        notificationManager.notify(
                                isConversation ? conversationNotificationId : flarelaneNotification.currentAndroidNotificationId(),
                                notification);

                        } finally {
                            if (conversationLock != null) {
                                conversationLock.unlock();
                            }
                        }

                        if (isGrouped) {
                            NotificationGroupManager.refreshSummary(
                                    context, threadId, flarelaneNotification.currentChannelId(context));
                        }

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

    /** Per-conversation locks so concurrent deliveries append to the MessagingStyle history
     *  atomically. Keyed by the stable conversation notification id; entries are tiny and the
     *  set of live conversations is small, so no eviction is needed. */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.locks.ReentrantLock> conversationLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static java.util.concurrent.locks.ReentrantLock conversationLockFor(int conversationNotificationId) {
        return conversationLocks.computeIfAbsent(conversationNotificationId, key -> new java.util.concurrent.locks.ReentrantLock());
    }

    /** Hard cap on downloaded image bytes; larger payloads fall back to a text-only notification. */
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    /** Decoded bitmaps are downsampled to fit this edge so a huge source can't OOM the process. */
    private static final int MAX_IMAGE_DIMENSION = 2048;

    /** Bounded, best-effort bitmap fetch shared by the big-picture image and the sender avatar. */
    private Bitmap downloadBitmap(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();

            // Buffer with a byte cap first — the stream can only be decoded once, and the size
            // check must happen before any decode allocates memory.
            byte[] bytes;
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                int total = 0;
                while ((read = in.read(chunk)) != -1) {
                    total += read;
                    if (total > MAX_IMAGE_BYTES) {
                        Logger.verbose("Notification image exceeds the size limit, falling back without it");
                        return null;
                    }
                    buffer.write(chunk, 0, read);
                }
                bytes = buffer.toByteArray();
            }

            // Inspect dimensions without allocating pixels, then downsample to a safe size.
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, boundsOptions);
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = 1;
            while (boundsOptions.outWidth / decodeOptions.inSampleSize > MAX_IMAGE_DIMENSION
                    || boundsOptions.outHeight / decodeOptions.inSampleSize > MAX_IMAGE_DIMENSION) {
                decodeOptions.inSampleSize *= 2;
            }

            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOptions);
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private int getNotificationIcon(Context context) {
        // Shared with the group summary so children and summary always match.
        return NotificationGroupManager.resolveSmallIcon(context);
    }

}

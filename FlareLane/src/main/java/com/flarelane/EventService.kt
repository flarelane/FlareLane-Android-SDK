package com.flarelane

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON merge for outgoing event data. Kept as a separate object (same file) so JVM unit
 * tests can verify the "host-supplied keys win" rule without loading EventService's HTTP layer.
 */
internal object EventDataMerger {
    @JvmStatic
    fun mergeSessionMetadata(data: JSONObject, sessionId: Long, sessionForegroundMs: Long): JSONObject {
        if (!data.has("sessionId")) data.put("sessionId", sessionId)
        if (!data.has("sessionForegroundMs")) data.put("sessionForegroundMs", sessionForegroundMs)
        return data
    }
}

internal object EventService {
    @JvmField
    var unhandledClickedNotification: Notification? = null

    @Throws(Exception::class)
    fun createNotificationClicked(
        projectId: String,
        deviceId: String,
        notification: Notification,
        userId: String?
    ) {
        sendDedupEvent(
            eventType = EventType.Clicked,
            projectId = projectId,
            deviceId = deviceId,
            notification = notification,
            userId = userId,
            dataBuilder = {
                JSONObject().apply {
                    val button = notification.clickedButton
                    if (button != null) {
                        put("isButton", true)
                        put("buttonIndex", notification.clickedButtonIdx)
                        put("buttonLabel", button.label)
                    }
                    val url = notification.clickedUrl
                    if (!url.isNullOrEmpty()) put("url", url)
                }
            },
            afterEmit = {
                if (FlareLane.notificationClickedHandler != null) {
                    FlareLane.notificationClickedHandler.onClicked(notification)
                } else {
                    unhandledClickedNotification = notification
                }
            }
        )
    }

    @Throws(Exception::class)
    fun executeInAppMessageAction(
        context: Context,
        iam: InAppMessage,
        actionId: String
    ) {
        val projectId = BaseSharedPreferences.getProjectId(context, false)
        val deviceId = BaseSharedPreferences.getDeviceId(context, false)
//        EventService.create(projectId, deviceId, notification.id, EventType.Clicked);

        if (FlareLane.inAppMessageActionHandler != null) {
            FlareLane.inAppMessageActionHandler.onExecute(iam, actionId)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createBackgroundReceived(projectId: String, deviceId: String, notification: Notification, userId: String?) {
        sendDedupEvent(EventType.BackgroundReceived, projectId, deviceId, notification, userId)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createForegroundReceived(projectId: String, deviceId: String, notification: Notification, userId: String?) {
        sendDedupEvent(EventType.ForegroundReceived, projectId, deviceId, notification, userId)
    }

    /**
     * Single entry point for all SDK-auto events. Runs dedup, builds optional event data, sends
     * the server event, and finally invokes the optional `afterEmit` block (e.g. notifying the
     * click handler). All steps are skipped on duplicate.
     */
    private fun sendDedupEvent(
        eventType: String,
        projectId: String,
        deviceId: String,
        notification: Notification,
        userId: String?,
        dataBuilder: (() -> JSONObject)? = null,
        afterEmit: (() -> Unit)? = null
    ) {
        if (EventDeduplicator.markAndCheckDuplicate(eventType, notification.id)) {
            Logger.verbose("Event", "duplicate prevented", mapOf("eventType" to eventType, "notificationId" to notification.id))
            return
        }
        // Guarantee afterEmit runs even if server event send throws — the user action
        // (e.g. click handler) must not be dropped because of a transient network failure.
        // The dedup mark is already in place, so cross-restart retry is server-side concern.
        try {
            val data = dataBuilder?.invoke() ?: JSONObject()
            injectSessionId(data)
            create(projectId, deviceId, notification.id, eventType, userId, data)
        } finally {
            afterEmit?.invoke()
        }
    }

    /**
     * Attach session metadata (sessionId + cumulative foreground time) to outgoing event payload.
     * Both ride in the event `data` k/v rather than as top-level fields so server schema stays
     * unchanged and old SDKs without session support remain compatible.
     *
     * `sessionForegroundMs` lets the server compute precise session length as
     * `max(sessionForegroundMs)` per sessionId — robust against process kill since each event
     * carries an up-to-that-point snapshot, no need for an unreliable session_end event.
     */
    private fun injectSessionId(data: JSONObject) {
        val ctx = FlareLane.getApplicationContext() ?: return
        EventDataMerger.mergeSessionMetadata(
            data,
            sessionId = SessionManager.currentSessionId(ctx),
            sessionForegroundMs = SessionManager.currentSessionForegroundMs(ctx)
        )
        SessionManager.touch(ctx)
    }

    @Throws(Exception::class)
    private fun create(
        projectId: String,
        deviceId: String,
        notificationId: String,
        type: String,
        userId: String?,
        data: JSONObject? = null
    ) {
        val body = JSONObject()
        body.put("notificationId", notificationId)
        body.put("deviceId", deviceId)
        body.put("platform", Constants.SDK_PLATFORM)
        body.put("type", type)
        body.put("createdAt", Utils.getISO8601DateString())

        if (userId != null) {
            body.put("userId", userId)
        }

        if (data != null && data.length() > 0) {
            body.put("data", data)
        }

        HTTPClient.post(
            "internal/v1/projects/$projectId/events",
            body,
            HTTPClient.ResponseHandler()
        )
    }

    @JvmStatic
    @Throws(Exception::class)
    fun trackEvent(
        projectId: String,
        deviceId: String,
        userId: String?,
        type: String,
        data: JSONObject?
    ) {
        val subjectType = if (userId != null) "user" else "device"
        val subjectId = userId ?: deviceId

        val eventData = data ?: JSONObject()
        injectSessionId(eventData)

        val event = JSONObject()
            .put("type", type)
            .put("subjectType", subjectType)
            .put("subjectId", subjectId)
            .put("createdAt", Utils.getISO8601DateString())
            .put("platform", Constants.SDK_PLATFORM)
            .put("deviceId", deviceId)

        if (eventData.length() > 0) {
            event.put("data", eventData)
        }

        if (userId != null) {
            event.put("userId", userId)
        }

        val body = JSONObject().put("events", JSONArray().put(event))

        HTTPClient.post(
            "internal/v1/projects/$projectId/events-v2",
            body,
            HTTPClient.ResponseHandler()
        )
    }
}

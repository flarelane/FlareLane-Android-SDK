package com.flarelane

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object EventService {
    @JvmField
    var unhandledClickedNotification: Notification? = null

    private const val CLICK_DEDUP_TTL_MS = 60_000L
    private const val CLICK_DEDUP_MAX_SIZE = 256
    private val processedClickIds = LinkedHashMap<String, Long>()

    @Synchronized
    private fun shouldSkipDuplicateClick(notificationId: String): Boolean {
        val now = System.currentTimeMillis()
        val iterator = processedClickIds.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > CLICK_DEDUP_TTL_MS) {
                iterator.remove()
            } else {
                break
            }
        }
        if (processedClickIds.containsKey(notificationId)) {
            Logger.verbose("Duplicate notification click prevented: $notificationId")
            return true
        }
        processedClickIds[notificationId] = now
        if (processedClickIds.size > CLICK_DEDUP_MAX_SIZE) {
            val oldest = processedClickIds.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        return false
    }

    @Throws(Exception::class)
    fun createNotificationClicked(
        projectId: String,
        deviceId: String,
        notification: Notification,
        userId: String?
    ) {
        if (shouldSkipDuplicateClick(notification.id)) {
            return
        }

        create(projectId, deviceId, notification.id, EventType.Clicked, userId)

        if (FlareLane.notificationClickedHandler != null) {
            FlareLane.notificationClickedHandler.onClicked(notification)
        } else {
            unhandledClickedNotification = notification
        }
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
        create(projectId, deviceId, notification.id, EventType.BackgroundReceived, userId)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createForegroundReceived(projectId: String, deviceId: String, notification: Notification, userId: String?) {
        create(projectId, deviceId, notification.id, EventType.ForegroundReceived, userId)
    }

    @Throws(Exception::class)
    private fun create(projectId: String, deviceId: String, notificationId: String, type: String, userId: String?) {
        val body = JSONObject()
        body.put("notificationId", notificationId)
        body.put("deviceId", deviceId)
        body.put("platform", Constants.SDK_PLATFORM)
        body.put("type", type)
        body.put("createdAt", Utils.getISO8601DateString())

        if (userId != null) {
            body.put("userId", userId)
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

        val event = JSONObject()
            .put("type", type)
            .put("subjectType", subjectType)
            .put("subjectId", subjectId)
            .put("createdAt", Utils.getISO8601DateString())
            .put("platform", Constants.SDK_PLATFORM)
            .put("deviceId", deviceId)

        if (data != null) {
            event.put("data", data)
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

package com.flarelane

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object EventService {
    @JvmField
    var unhandledClickedNotification: Notification? = null

    @Throws(Exception::class)
    fun createNotificationClicked(
        projectId: String,
        deviceId: String,
        notification: Notification
    ) {
        create(projectId, deviceId, notification.id, EventType.Clicked)

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
    fun createBackgroundReceived(projectId: String, deviceId: String, notification: Notification) {
        create(projectId, deviceId, notification.id, EventType.BackgroundReceived)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createForegroundReceived(projectId: String, deviceId: String, notification: Notification) {
        create(projectId, deviceId, notification.id, EventType.ForegroundReceived)
    }

    @Throws(Exception::class)
    private fun create(projectId: String, deviceId: String, notificationId: String, type: String) {
        val body = JSONObject()
        body.put("notificationId", notificationId)
        body.put("deviceId", deviceId)
        body.put("platform", "android")
        body.put("type", type)
        body.put("createdAt", Utils.getISO8601DateString())

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
        subjectType: String?,
        subjectId: String?,
        type: String?,
        data: JSONObject?
    ) {
        val event = JSONObject()
            .put("type", type)
            .put("subjectType", subjectType)
            .put("subjectId", subjectId)
            .put("createdAt", Utils.getISO8601DateString())

        if (data != null) {
            event.put("data", data)
        }

        val body = JSONObject().put("events", JSONArray().put(event))

        HTTPClient.post(
            "internal/v1/projects/$projectId/events-v2",
            body,
            HTTPClient.ResponseHandler()
        )
    }
}

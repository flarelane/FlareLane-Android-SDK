package com.flarelane;

import com.flarelane.notification.NotificationClickEvent;

import org.json.JSONArray;
import org.json.JSONObject;

class EventService {
    protected static NotificationClickEvent unhandledNotificationClickEvent;

    protected static void createNotificationClicked(
            String projectId,
            String deviceId,
            NotificationClickEvent event
    ) throws Exception {
        EventService.create(projectId, deviceId, event.notification.id, EventType.NOTIFICATION_CLICKED);

        if (FlareLane.notificationClickedHandler != null) {
            FlareLane.notificationClickedHandler.onClicked(event);
        } else {
            EventService.unhandledNotificationClickEvent = event;
        }
    }

    protected static void createNotificationButtonClicked(
            String projectId,
            String deviceId,
            NotificationClickEvent event
    ) throws Exception {
        if (event.notificationClickedButton == null) {
            return;
        }

        if (FlareLane.notificationClickedHandler != null) {
            FlareLane.notificationClickedHandler.onClicked(event);
        } else {
            EventService.unhandledNotificationClickEvent = event;
        }

        JSONObject body = new JSONObject();
        body.put("type", EventType.NOTIFICATION_BUTTON_CLICKED);
        body.put("platform", "android");
        body.put("deviceId", deviceId);
        body.put("createdAt", Utils.getISO8601DateString());
        body.put("notificationId", event.notification.id);
        body.put("buttonId", event.notificationClickedButton.id);

        HTTPClient.post("internal/v1/projects/" + projectId + "/events-v2", body, new HTTPClient.ResponseHandler());
    }

    protected static void createBackgroundReceived(String projectId, String deviceId, Notification notification) throws Exception {
        EventService.create(projectId, deviceId, notification.id, EventType.BACKGROUND_RECEIVED);
    }

    protected static void createForegroundReceived(String projectId, String deviceId, Notification notification) throws Exception {
        EventService.create(projectId, deviceId, notification.id, EventType.FOREGROUND_RECEIVED);
    }

    private static void create(String projectId, String deviceId, String notificationId, String type) throws Exception {
        JSONObject body = new JSONObject();
        body.put("notificationId", notificationId);
        body.put("deviceId", deviceId);
        body.put("platform", "android");
        body.put("type", type);
        body.put("createdAt", Utils.getISO8601DateString());

        HTTPClient.post("internal/v1/projects/" + projectId + "/events", body, new HTTPClient.ResponseHandler());
    }

    protected static void trackEvent(String projectId, String subjectType, String subjectId, String type, JSONObject data) throws Exception {
        JSONObject event = new JSONObject()
                .put("type", type)
                .put("subjectType", subjectType)
                .put("subjectId", subjectId)
                .put("createdAt", Utils.getISO8601DateString());

        if (data != null) {
            event.put("data", data);
        }

        JSONObject body = new JSONObject().put("events", new JSONArray().put(event));

        HTTPClient.post("internal/v1/projects/" + projectId + "/events-v2", body, new HTTPClient.ResponseHandler());
    }
}

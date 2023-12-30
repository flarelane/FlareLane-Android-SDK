package com.flarelane;

import android.util.EventLog;

import org.json.JSONArray;
import org.json.JSONObject;

class EventService {
    static Notification unhandledClickedNotification;

    static protected void createClicked(String projectId, String deviceId, Notification notification) throws Exception {
        EventService.create(projectId, deviceId, notification.id, EventType.Clicked);

        if (FlareLane.notificationClickedHandler != null) {
            FlareLane.notificationClickedHandler.onClicked(notification);
        } else {
            EventService.unhandledClickedNotification = notification;
        }
    }

    static protected void createBackgroundReceived(String projectId, String deviceId, Notification notification) throws Exception {
        EventService.create(projectId, deviceId, notification.id, EventType.BackgroundReceived);
    }

    static protected void createForegroundReceived(String projectId, String deviceId, Notification notification) throws Exception {
        EventService.create(projectId, deviceId, notification.id, EventType.ForegroundReceived);
    }

    static private void create(String projectId, String deviceId, String notificationId, String type) throws Exception {
        JSONObject body = new JSONObject();
        body.put("notificationId", notificationId);
        body.put("deviceId", deviceId);
        body.put("platform", "android");
        body.put("type", type);
        body.put("createdAt", Utils.getISO8601DateString());

        HTTPClient.post("internal/v1/projects/" + projectId + "/events", body, new HTTPClient.ResponseHandler());
    }

    static protected void trackEvent(String projectId, String subjectType, String subjectId, String type, JSONObject data) throws Exception {
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

package com.flarelane;

import android.util.EventLog;

import org.json.JSONObject;

class EventService {
    static Notification unhandledConvertedNotification;

    static protected void createConverted(String projectId, String deviceId, Notification notification) throws Exception {
        EventService.create(projectId, deviceId, notification.id, EventType.Converted);

        if (com.flarelane.FlareLane.notificationConvertedHandler != null) {
            com.flarelane.FlareLane.notificationConvertedHandler.onConverted(notification);
        } else {
            EventService.unhandledConvertedNotification = notification;
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
}

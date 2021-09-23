package com.flarelane;

import org.json.JSONObject;

class EventService {
    static public void create(String projectId, String deviceId, String notificationId, String type) throws Exception {
        JSONObject body = new JSONObject();
        body.put("notificationId", notificationId);
        body.put("deviceId", deviceId);
        body.put("platform", "android");
        body.put("type", type);
        body.put("createdAt", Utils.getISO8601DateString());

        HTTPClient.post("internal/v1/projects/" + projectId + "/events", body, new HTTPClient.ResponseHandler());
    }
}

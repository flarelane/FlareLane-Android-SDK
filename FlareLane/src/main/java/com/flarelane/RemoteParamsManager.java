package com.flarelane;

import org.json.JSONObject;

class RemoteParamsManager {
    static RemoteParams remoteParams;

    static void fetchRemoteParams(String projectId, ResponseHandler handler) {
        if (remoteParams != null) return;

        HTTPClient.get("internal/v1/projects/" + projectId + "/remote-params", new HTTPClient.ResponseHandler() {
            @Override
            void onSuccess(int responseCode, JSONObject response) {
                super.onSuccess(responseCode, response);

                try {
                    Logger.verbose("fetchRemoteParams Success: " + response.toString());
                    JSONObject data = response.getJSONObject("data");

                    String senderId = data.isNull("fcmSenderId") ? null : data.getString("fcmSenderId");

                    remoteParams = new RemoteParams(senderId);
                    handler.onSuccess(remoteParams);
                } catch (Exception e) {
                    BaseErrorHandler.handle(e);
                }
            }
        });
    }

    protected interface ResponseHandler {
        void onSuccess(RemoteParams remoteParams);
    }
}

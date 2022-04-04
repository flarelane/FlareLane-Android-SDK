package com.flarelane;

class RemoteParams {
    String fcmAppId;
    String fcmProjectId;
    String fcmApiKey;
    String fcmSenderId;

    public RemoteParams(String fcmAppId, String fcmProjectId, String fcmApiKey, String fcmSenderId) {
        this.fcmAppId = fcmAppId;
        this.fcmProjectId = fcmProjectId;
        this.fcmApiKey = fcmApiKey;
        this.fcmSenderId = fcmSenderId;
    }
}

package com.flarelane;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

class FirebaseManager {
    private static final String FLARELANE_FIREBASE_APP_NAME = "FirebaseOfFlareLane";
    private static FirebaseApp firebaseApp;

    public static FirebaseMessaging getFirebaseMessaging(Context context, RemoteParams remoteParams) throws Exception {
        if (firebaseApp == null) {
            ApplicationInfo ai = context.getApplicationContext().getPackageManager().getApplicationInfo(context.getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);

            String appId = remoteParams.fcmAppId != null ? remoteParams.fcmAppId : ai.metaData.getString("com.flarelane.default_firebase_app_id");
            String projectId = remoteParams.fcmProjectId != null ? remoteParams.fcmProjectId : ai.metaData.getString("com.flarelane.default_firebase_project_id");
            String apiKey = remoteParams.fcmApiKey != null ? remoteParams.fcmApiKey : ai.metaData.getString("com.flarelane.default_firebase_api_key");

            FirebaseManager.firebaseApp = FirebaseApp.initializeApp(context,
                    new FirebaseOptions.Builder()
                            .setApplicationId(appId)
                            .setProjectId(projectId)
                            .setApiKey(apiKey)
                            .setGcmSenderId(remoteParams.fcmSenderId)
                            .build(),
                    FLARELANE_FIREBASE_APP_NAME
            );
        }

        return firebaseApp.get(FirebaseMessaging.class);
    }
}

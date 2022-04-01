package com.flarelane;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

class FirebaseManager {
    private static final String FLARELANE_FIREBASE_APP_NAME = "FirebaseOfFlareLane";
    private static FirebaseApp firebaseApp;

    public static FirebaseMessaging getFirebaseMessaging(Context context, String senderId) {
        if (firebaseApp == null) {
            FirebaseManager.firebaseApp = FirebaseApp.initializeApp(context,
                    new FirebaseOptions.Builder()
                            .setApplicationId(context.getString(R.string.flarelane_firebase_app_id))
                            .setProjectId(context.getString(R.string.flarelane_firebase_project_id))
                            .setApiKey(context.getString(R.string.flarelane_firebase_api_key))
                            .setGcmSenderId(senderId)
                            .build(),
                    FLARELANE_FIREBASE_APP_NAME
            );
        }

        return firebaseApp.get(FirebaseMessaging.class);
    }
}

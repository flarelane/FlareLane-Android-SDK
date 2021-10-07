package com.flarelane;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

class FirebaseManager {
    private static final String FLARELANE_FIREBASE_APP_ID = "1:415602473946:android:98a2ad40916ab031e59ee3";
    private static final String FLARELANE_FIREBASE_PROJECT_ID = "flarelane-public";
    private static final String FLARELANE_FIREBASE_API_KEY = "AIzaSyC44DyZywKLyQH3AVXZrLbGu7VDJosMKQ0";
    private static final String FLARELANE_FIREBASE_APP_NAME = "FirebaseOfFlareLane";
    private static FirebaseApp firebaseApp;


    public static FirebaseMessaging getFirebaseMessaging(Context context, String senderId) {
        if (firebaseApp == null) {
            FirebaseManager.firebaseApp = FirebaseApp.initializeApp(context,
                    new FirebaseOptions.Builder()
                            .setApplicationId(FLARELANE_FIREBASE_APP_ID)
                            .setProjectId(FLARELANE_FIREBASE_PROJECT_ID)
                            .setApiKey(FLARELANE_FIREBASE_API_KEY)
                            .setGcmSenderId(senderId)
                            .build(),
                    FLARELANE_FIREBASE_APP_NAME
            );
        }

        return firebaseApp.get(FirebaseMessaging.class);
    }
}

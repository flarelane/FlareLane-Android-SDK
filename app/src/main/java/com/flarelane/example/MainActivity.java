package com.flarelane.example;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.flarelane.FlareLane;
import com.flarelane.Notification;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        askNotificationPermission();
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.d("FlareLane", "Fetching FCM registration token failed", task.getException());
                            return;
                        }
                        String token = task.getResult();
                        Log.d("FlareLane", "Example FCM Token: " + token);
                    }
                });

        Context context = this;

        Button setUserId = findViewById(R.id.setUserIdButton);
        setUserId.setOnClickListener(new View.OnClickListener() {
            String userId = null;

            @Override
            public void onClick(View v) {
                FlareLane.setUserId(context, userId);
                userId = userId == null ? "myuser@flarelane.com" : null;
            }
        });

        Button getTagsButton = findViewById(R.id.getTagsButton);
        getTagsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FlareLane.getTags(context, new FlareLane.GetTagsHandler() {
                    @Override
                    public void onReceiveTags(JSONObject tags) {
                        Log.d("FlareLane", "Received Tags: " + tags);
                    }
                });
            }
        });

        Button setTagsButton = findViewById(R.id.setTagsButton);
        setTagsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("age", 27);
                    data.put("gender", "men");

                    FlareLane.setTags(context, data);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        Button deleteTagsButton = findViewById(R.id.deleteTagsButton);
        deleteTagsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> keys = new ArrayList<String>();
                keys.add("age");
                keys.add("gender");

                FlareLane.deleteTags(context, keys);
            }
        });

        Button trackEventButton = findViewById(R.id.trackEventButton);
        trackEventButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("num", 10);
                    data.put("str", "hello world");

                    FlareLane.trackEvent(context, "test_event", data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Button isSubscribedButton = findViewById(R.id.isSubscribedButton);
        isSubscribedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isSubscribed = FlareLane.isSubscribed(context);
                Log.d("FlareLane", "isSubscribed(): " + isSubscribed);
            }
        });

        Button subscribeButton = findViewById(R.id.subscribeButton);
        subscribeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FlareLane.subscribe(context, true, new FlareLane.IsSubscribedHandler() {
                    @Override
                    public void onSuccess(boolean isSubscribed) {
                        Log.d("FlareLane", "subscribe(): " + isSubscribed);
                    }
                });
            }
        });

        Button unsubscribeButton = findViewById(R.id.unsubscribeButton);
        unsubscribeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FlareLane.unsubscribe(context, new FlareLane.IsSubscribedHandler() {
                    @Override
                    public void onSuccess(boolean isSubscribed) {
                        Log.d("FlareLane", "unsubscribe(): " + isSubscribed);
                    }
                });
            }
        });
    }

    // FOR FIREBASE: https://firebase.google.com/docs/cloud-messaging/android/client
    // Declare the launcher at the top of your Activity/Fragment:
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // FCM SDK (and your app) can post notifications.
                } else {
                    // TODO: Inform user that that your app will not show notifications.
                }
            });

    private void askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}

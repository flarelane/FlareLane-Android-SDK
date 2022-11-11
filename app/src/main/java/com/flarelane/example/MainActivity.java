package com.flarelane.example;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.flarelane.FlareLane;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull Task<String> task) {
                    if (!task.isSuccessful()) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d("FCM", token);
                }
            });


        Context context = this;

        Button setIsSubscribedButton = findViewById(R.id.setIsSubscribedButton);
        setIsSubscribedButton.setOnClickListener(new View.OnClickListener() {
            boolean isSubscribed = true;

            @Override
            public void onClick(View v) {
                FlareLane.setIsSubscribed(context, isSubscribed);
                isSubscribed = !isSubscribed;
            }
        });

        Button setUserId = findViewById(R.id.setUserIdButton);
        setUserId.setOnClickListener(new View.OnClickListener() {
            String userId = null;

            @Override
            public void onClick(View v) {
                FlareLane.setUserId(context, userId);
                userId = userId == null ? "myuser@flarelane.com" : null;
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
    }
}

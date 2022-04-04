package com.flarelane;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

public class NotificationConvertedActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            if (getIntent().hasExtra("notificationId")) {
                String notificationId = getIntent().getStringExtra("notificationId");

                String projectId = com.flarelane.BaseSharedPreferences.getProjectId(this.getApplicationContext(), false);
                String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(this.getApplicationContext(), false);

                com.flarelane.Notification notification = new com.flarelane.Notification(
                        notificationId,
                        getIntent().getStringExtra("body"),
                        getIntent().getStringExtra("title"),
                        getIntent().getStringExtra("url"),
                        getIntent().getStringExtra("imageUrl")
                );

                EventService.createConverted(projectId, deviceId, notification);

                startLaunchActivityIfNoHistory();
            }
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        } finally {
            finish();
        }
    }

    private void startLaunchActivityIfNoHistory() {
        if (isTaskRoot()) {
            com.flarelane.Logger.verbose("This is last activity in the stack");

            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            startActivity(launchIntent);
        }
    }
}

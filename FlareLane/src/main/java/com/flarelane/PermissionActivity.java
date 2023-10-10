package com.flarelane;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class PermissionActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askNotificationPermission();
    }

    private void askNotificationPermission() {
        Application application = (Application) this.getApplicationContext();
        int targetSdkVersion = application.getApplicationInfo().targetSdkVersion;

//         Ask a permission if Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                targetSdkVersion >= Build.VERSION_CODES.TIRAMISU &&
                !(ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED)) {

            BaseSharedPreferences.setAlreadyPermissionAsked(application, true);
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 419);
        } else {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 419:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    FlareLane.updatePushToken(getApplicationContext(), new FlareLane.UpdatePushTokenHandler() {
                        @Override
                        public void onSuccess(String pushToken) {

                        }
                    });
                }
        }

        finish();
        overridePendingTransition(0, 0);
    }
}

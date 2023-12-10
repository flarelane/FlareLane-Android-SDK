package com.flarelane;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;

public class Helper {
    protected static boolean appInForeground(@NonNull Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = activityManager.getRunningAppProcesses();
        if (runningAppProcesses == null) {
            return false;
        }

        for (ActivityManager.RunningAppProcessInfo runningAppProcess : runningAppProcesses) {
            if (runningAppProcess.processName.equals(context.getPackageName()) &&
                    runningAppProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    protected static String getResourceString(@NonNull Context context, @NonNull String identifier) {
        int resourceId = context.getApplicationContext().getResources().getIdentifier(identifier, "string", context.getApplicationContext().getPackageName());
        if (resourceId != 0 ) {
            String resourceString = context.getApplicationContext().getResources().getString(resourceId);
            return resourceString;
        }

        return null;
    }
}

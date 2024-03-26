package com.flarelane;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ActivityLifecycleManager {
    private final Set<Activity> activitySet = new HashSet<>();
    private final List<Class<?>> skipActivityList = Arrays.asList(
        PermissionActivity.class,
        NotificationClickedActivity.class
    );

    private boolean isSkipActivity(Activity activity) {
        return skipActivityList.contains(activity.getClass());
    }

    protected Application.ActivityLifecycleCallbacks mActivityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            if (isSkipActivity(activity)) {
                return;
            }
            activitySet.add(activity);
            FlareLane.deviceRegisterOrActivate(activity.getApplicationContext());
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            if (isSkipActivity(activity)) {
                return;
            }
            activitySet.remove(activity);
            if (activitySet.isEmpty()) {
                FlareLane.isActivated = false;
            }
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }
    };
}
package com.flarelane;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class ActivityLifecycleManager {
    private boolean isActivated = false;
    protected Application.ActivityLifecycleCallbacks mActivityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
//            TODO: ReactNative 콜백 등록 타이밍 이슈로 Paused 시 activate 체크
            try {
                if (isActivated == false) {
                    Context context = activity.getApplicationContext();
                    String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, true);
                    String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, true);
                    String pushToken = com.flarelane.BaseSharedPreferences.getPushToken(context, true);

                    if (projectId != null && deviceId != null & pushToken != null) {
                        com.flarelane.Logger.verbose("App is activated");
                        isActivated = true;
                        com.flarelane.DeviceService.activate(activity.getApplicationContext(), projectId, deviceId, pushToken);
                    }
                }
            } catch (Exception e) {
                BaseErrorHandler.handle(e);
            }
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    };
}

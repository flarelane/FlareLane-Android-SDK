package com.flarelane;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.util.ArrayList;


public class FlareLane {
    public static class SdkInfo {
        public static SdkType type = SdkType.NATIVE;
        public static String version = "1.1.0";
    }

    protected static com.flarelane.NotificationConvertedHandler notificationConvertedHandler = null;
    private static com.flarelane.ActivityLifecycleManager activityLifecycleManager = new com.flarelane.ActivityLifecycleManager();
    protected static int notificationIcon = 0;

    public static void initWithContext(Context context, String projectId) {
        try {
            com.flarelane.Logger.verbose("initWithContext projectId: " + projectId);
            com.flarelane.ChannelManager.createNotificationChannel(context);

            // If projectId is null or different, reset savedDeviceId to null
            String savedProjectId = com.flarelane.BaseSharedPreferences.getProjectId(context, true);
            if (savedProjectId == null || !savedProjectId.contentEquals(projectId)) {
                com.flarelane.BaseSharedPreferences.setDeviceId(context, null);
            }

            Application application = (Application) context.getApplicationContext();
            application.registerActivityLifecycleCallbacks(activityLifecycleManager.mActivityLifecycleCallbacks);

            RemoteParamsManager.fetchRemoteParams(projectId, new RemoteParamsManager.ResponseHandler() {
                @Override
                public void onSuccess(RemoteParams remoteParams) {
                    try {
                        if (remoteParams.fcmSenderId == null) {
                            Logger.error("senderId is null. Please check a property of your project");
                            return;
                        }

                        String savedDeviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, true);
                        String savedPushToken = com.flarelane.BaseSharedPreferences.getPushToken(context, true);

                        Task<String> getTokenTask = FirebaseManager.getFirebaseMessaging(context, remoteParams).getToken();
                        getTokenTask.addOnCompleteListener(new OnCompleteListener<String>() {
                            @Override
                            public void onComplete(@NonNull Task<String> task) {
                                try {
                                    if (!task.isSuccessful()) {
                                        Logger.error("Fetching FCM registration token failed: " + task.getException());
                                        return;
                                    }

                                    // Get new FCM registration token
                                    String token = task.getResult();
                                    if (token == null) {
                                        com.flarelane.Logger.error("token is null");
                                        return;
                                    }

                                    com.flarelane.Logger.verbose("FirebaseMessaging.getInstance().getToken() is Completed");

                                    if (savedPushToken == null || !savedPushToken.contentEquals(token)) {
                                        com.flarelane.Logger.verbose("new PushToken is saved");
                                        com.flarelane.BaseSharedPreferences.setPushToken(context, token);
                                    }

                                    if (savedDeviceId == null || savedDeviceId.trim().isEmpty()) {
                                        com.flarelane.Logger.verbose("savedDeviceId is not exists, newly registered");
                                        com.flarelane.DeviceService.register(context, projectId, token);
                                    } else {
                                        com.flarelane.Logger.verbose("savedDeviceId is exists : " + savedDeviceId);
                                    }

                                } catch (Exception e) {
                                    com.flarelane.BaseErrorHandler.handle(e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        BaseErrorHandler.handle(e);
                    }
                }
            });
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    // TODO: (Deprecated) FlareLane 클래스 내 코드로 아이콘 변경할 수 없도록 할 예정 (기본 리소스 이름을 인식하게 하거나, Notification 의 변수 값으로 동적 할당 예정)
    public static void setNotificationIcon(int notificationIcon) {
        FlareLane.notificationIcon = notificationIcon;
    }

    public static void setLogLevel(int logLevel) {
        try {
            com.flarelane.Logger.logLevel = logLevel;
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void setNotificationConvertedHandler(com.flarelane.NotificationConvertedHandler notificationConvertedHandler) {
        try {
            FlareLane.notificationConvertedHandler = notificationConvertedHandler;

            if (EventService.unhandledConvertedNotification != null) {
                notificationConvertedHandler.onConverted(EventService.unhandledConvertedNotification);
                EventService.unhandledConvertedNotification = null;
            }
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void setIsSubscribed(Context context, boolean isSubscribed) {
        try {
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);
            String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, false);

            JSONObject data = new JSONObject();
            data.put("isSubscribed", isSubscribed);

            com.flarelane.DeviceService.update(projectId, deviceId, data, new com.flarelane.DeviceService.ResponseHandler() {
                @Override
                public void onSuccess(com.flarelane.Device device) {

                }
            });
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void setUserId(Context context, String userId) {
        try {
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);
            String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, false);

            JSONObject data = new JSONObject();
            data.put("userId", userId == null ? JSONObject.NULL : userId);

            com.flarelane.DeviceService.update(projectId, deviceId, data, new com.flarelane.DeviceService.ResponseHandler() {
                @Override
                public void onSuccess(com.flarelane.Device device) {

                }
            });
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void setTags(Context context, JSONObject tags) {
        try {
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);
            String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, false);

            JSONObject data = new JSONObject();
            data.put("tags", tags);

            com.flarelane.DeviceService.update(projectId, deviceId, data, new com.flarelane.DeviceService.ResponseHandler() {
                @Override
                public void onSuccess(com.flarelane.Device device) {

                }
            });
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void deleteTags(Context context, ArrayList<String> keys) {
        try {
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);
            String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, false);

            com.flarelane.DeviceService.deleteTags(projectId, deviceId, keys);
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static String getDeviceId(Context context) {
        try {
            String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, true);
            return deviceId;
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }

        return null;
    }
}

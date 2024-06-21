package com.flarelane;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.flarelane.webview.FlareLaneInAppWebViewActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

import java.util.ArrayList;


public class FlareLane {
    public static class SdkInfo {
        public static SdkType type = SdkType.NATIVE;
        public static String version = "1.6.2";
    }

    protected static com.flarelane.NotificationForegroundReceivedHandler notificationForegroundReceivedHandler = null;
    protected static com.flarelane.NotificationClickedHandler notificationClickedHandler = null;
    protected static int notificationIcon = 0;
    protected static boolean requestPermissionOnLaunch = false;
    private static Handler mainHandler = new Handler(Looper.getMainLooper());
    protected static boolean isActivated = false;

    private static com.flarelane.ActivityLifecycleManager activityLifecycleManager = new com.flarelane.ActivityLifecycleManager();


    public static void initWithContext(Context context, String projectId, boolean requestPermissionOnLaunch) {
        try {
            FlareLane.requestPermissionOnLaunch = requestPermissionOnLaunch;
            com.flarelane.Logger.verbose("initWithContext projectId: " + projectId);
            com.flarelane.ChannelManager.createNotificationChannel(context);

            // If projectId is null or different, reset savedDeviceId to null
            String savedProjectId = com.flarelane.BaseSharedPreferences.getProjectId(context, true);
            if (savedProjectId == null || !savedProjectId.contentEquals(projectId)) {
                com.flarelane.BaseSharedPreferences.setDeviceId(context, null);
                com.flarelane.BaseSharedPreferences.setIsSubscribed(context, false);
                com.flarelane.BaseSharedPreferences.setProjectId(context, projectId);
            }

            deviceRegisterOrActivate(context);

            Application application = (Application) context.getApplicationContext();
            application.registerActivityLifecycleCallbacks(activityLifecycleManager.mActivityLifecycleCallbacks);
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

    public static void setNotificationClickedHandler(com.flarelane.NotificationClickedHandler handler) {
        try {
            FlareLane.notificationClickedHandler = handler;

            if (EventService.unhandledClickedNotification != null) {
                handler.onClicked(EventService.unhandledClickedNotification);
                EventService.unhandledClickedNotification = null;
            }

            Logger.verbose("NotificationClickedHandler has been registered.");
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void setNotificationForegroundReceivedHandler(com.flarelane.NotificationForegroundReceivedHandler notificationForegroundReceivedHandler) {
        try {
            FlareLane.notificationForegroundReceivedHandler = notificationForegroundReceivedHandler;
            Logger.verbose("NotificationForegroundReceivedHandler has been registered.");
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void setUserId(Context context, String userId) {
        try {
            JSONObject data = new JSONObject();
            data.put("userId", userId == null ? JSONObject.NULL : userId);

            com.flarelane.DeviceService.update(context, data, new com.flarelane.DeviceService.ResponseHandler() {
                @Override
                public void onSuccess(com.flarelane.Device device) {
                    BaseSharedPreferences.setUserId(context, device.userId);
                }
            });
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void setTags(Context context, JSONObject tags) {
        try {
            JSONObject data = new JSONObject();
            data.put("tags", tags);

            com.flarelane.DeviceService.update(context, data, new com.flarelane.DeviceService.ResponseHandler() {
                @Override
                public void onSuccess(com.flarelane.Device device) {

                }
            });
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

    public static boolean isSubscribed(Context context) {
        try {
            // As cannot controlled, not check targetSdkVersion.
            boolean hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            String savedIsSubscribed = com.flarelane.BaseSharedPreferences.getIsSubscribed(context, true);

            return hasPermission && savedIsSubscribed != null && savedIsSubscribed.contentEquals("true");
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
        return false;
    }

    public static void subscribe(Context context, boolean fallbackToSettings, @Nullable IsSubscribedHandler handler) {
        try {
            PermissionActivity.isSubscribedHandler = handler;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
                Application application = (Application) context.getApplicationContext();
                int targetSdkVersion = application.getApplicationInfo().targetSdkVersion;

                // If targetSdkVersion < Build.VERSION_CODES.TIRAMISU, must go to settings.
                if (targetSdkVersion < Build.VERSION_CODES.TIRAMISU ||
                        (fallbackToSettings && BaseSharedPreferences.getAlreadyPermissionAsked(context))) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent();
                            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
                            context.startActivity(intent);
                        }
                    }).start();
                } else {
                    requestPermissionForNotifications(context, handler);
                }
            } else {
                subscribeWithPushToken(context, handler);
            }
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void unsubscribe(Context context, @Nullable IsSubscribedHandler handler) {
        try {
            JSONObject data = new JSONObject();
            data.put("isSubscribed", false);

            DeviceService.update(context, data, new DeviceService.ResponseHandler() {
                @Override
                public void onSuccess(Device device) {
                    if (handler != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                handler.onSuccess(device.isSubscribed);
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    public static void trackEvent(Context context, String type, JSONObject data) {
        try {
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);
            String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, false);
            String userId = com.flarelane.BaseSharedPreferences.getUserId(context, true);

            String subjectType = userId != null ? "user" : "device";
            String subjectId = userId != null ? userId : deviceId;

            com.flarelane.EventService.trackEvent(projectId, subjectType, subjectId, type, data);
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    protected static void requestPermissionForNotifications(Context context, @Nullable IsSubscribedHandler handler) {
        Application application = (Application) context.getApplicationContext();
        int targetSdkVersion = application.getApplicationInfo().targetSdkVersion;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                targetSdkVersion >= Build.VERSION_CODES.TIRAMISU &&
                !(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            // Ask a permission if Android 13
            Intent intent = new Intent(context, PermissionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            subscribeWithPushToken(context, handler);
        }
    }

    protected static void deviceRegisterOrActivate(Context context) {
        try {
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, true);
            if (projectId == null || projectId.trim().isEmpty()) return;

            // Execute only once.
            if (!Helper.appInForeground(context) || isActivated) return;
            isActivated = true;

            String savedDeviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, true);
            if (savedDeviceId == null || savedDeviceId.trim().isEmpty()) {
                com.flarelane.Logger.verbose("savedDeviceId is not exists, newly registered");
                com.flarelane.DeviceService.register(context, projectId, new DeviceService.ResponseHandler() {
                    @Override
                    public void onSuccess(Device device) {
                        if (!FlareLane.isSubscribed(context) && com.flarelane.FlareLane.requestPermissionOnLaunch && Helper.appInForeground(context)) {
                            com.flarelane.FlareLane.requestPermissionForNotifications(context, null);
                        }
                    }
                });
            } else {
                com.flarelane.Logger.verbose("savedDeviceId is exists : " + savedDeviceId);
                com.flarelane.DeviceService.activate(context, new DeviceService.ResponseHandler() {
                    @Override
                    public void onSuccess(Device device) {
                        if (!FlareLane.isSubscribed(context) && com.flarelane.FlareLane.requestPermissionOnLaunch && Helper.appInForeground(context)) {
                            com.flarelane.FlareLane.requestPermissionForNotifications(context, null);
                        }
                    }
                });
            }
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
        }
    }

    public interface IsSubscribedHandler {
        void onSuccess(boolean isSubscribed);
    }

    protected interface GetPushTokenHandler {
        void onSuccess(String pushToken);
    }

    protected static void subscribeWithPushToken(Context context, @Nullable IsSubscribedHandler handler) {
        try {
            getPushToken(context, new GetPushTokenHandler() {
                @Override
                public void onSuccess(String pushToken) {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("isSubscribed", true);
                        data.put("pushToken", pushToken);

                        DeviceService.update(context, data, new DeviceService.ResponseHandler() {
                            @Override
                            public void onSuccess(Device device) {
                                if (handler != null) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            handler.onSuccess(device.isSubscribed);
                                        }
                                    });
                                }
                            }
                        });
                    } catch (Exception e) {
                        com.flarelane.BaseErrorHandler.handle(e);
                    }
                }
            });
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    private static void getPushToken(Context context, @Nullable GetPushTokenHandler handler) {
        try {
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
                Logger.verbose("updatePushToken failed: Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU, but POST_NOTIFICATIONS is not granted!");
                return;
            }

            RemoteParamsManager.fetchRemoteParams(projectId, new RemoteParamsManager.ResponseHandler() {
                @Override
                public void onSuccess(RemoteParams remoteParams) {
                    try {
                        if (remoteParams.fcmSenderId == null) {
                            Logger.error("senderId is null. Please check a property of your project");
                            return;
                        }

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

                                    if (handler != null)
                                        handler.onSuccess(token);
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

    public static void displayInApp(Activity activity) {
        try {
            String projectId = com.flarelane.BaseSharedPreferences.getProjectId(activity, false);
            String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(activity, false);
            InAppService.getMessage$FlareLane_debug(projectId, deviceId,  htmlString -> {
                FlareLaneInAppWebViewActivity.Companion.show$FlareLane_debug(activity, htmlString);
                return null;
            });
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }
}

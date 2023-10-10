package com.flarelane;

import android.content.Context;
import android.content.pm.PackageInfo;

class BaseSharedPreferences {
    private static final String SHARED_PREFERENCE_KEY_PREFIX = "com.flarelane.SHARED_PREFERENCE_KEY_";
    private static final String DEVICE_ID_KEY = "com.flarelane.DEVICE_ID_KEY";
    private static final String PROJECT_ID_KEY = "com.flarelane.PROJECT_ID_KEY";
    private static final String PUSH_TOKEN_KEY = "com.flarelane.PUSH_TOKEN_KEY";
    private static final String USER_ID_KEY = "com.flarelane.USER_ID_KEY";
    private static final String IS_SUBSCRIBED_KEY = "com.flarelane.IS_SUBSCRIBED_KEY";
    private static final String ALREADY_PERMISSION_ASKED_KEY = "com.flarelane.ALREADY_PERMISSION_ASKED_KEY";

    private static String getSharedPreferencesKey(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return SHARED_PREFERENCE_KEY_PREFIX + info.firstInstallTime;
        } catch (Exception e) {
            return SHARED_PREFERENCE_KEY_PREFIX;
        }
    }

    private static android.content.SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(getSharedPreferencesKey(context), Context.MODE_PRIVATE);
    }

    public static String getDeviceId(Context context, boolean nullable) throws NullValueException {
        String data = getSharedPreferences(context).getString(DEVICE_ID_KEY, null);
        if (nullable != true && data == null) throw new NullValueException("deviceId");
        return data;
    }

    public static boolean setDeviceId(Context context, String deviceId) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(DEVICE_ID_KEY, deviceId);
        return editor.commit();
    }

    public static String getProjectId(Context context, boolean nullable) throws NullValueException {
        String data = getSharedPreferences(context).getString(PROJECT_ID_KEY, null);
        if (nullable != true && data == null) throw new NullValueException("projectId");
        return data;
    }

    public static boolean setProjectId(Context context, String projectId) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(PROJECT_ID_KEY, projectId);
        return editor.commit();
    }

    public static String getPushToken(Context context, boolean nullable) throws NullValueException {
        String data = getSharedPreferences(context).getString(PUSH_TOKEN_KEY, null);
        if (nullable != true && data == null) throw new NullValueException("pushToken");
        return data;
    }

    public static boolean setPushToken(Context context, String pushToken) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(PUSH_TOKEN_KEY, pushToken);
        return editor.commit();
    }

    public static String getUserId(Context context, boolean nullable) throws NullValueException {
        String data = getSharedPreferences(context).getString(USER_ID_KEY, null);
        if (nullable != true && data == null) throw new NullValueException("userId");
        return data;
    }

    public static boolean setUserId(Context context, String userId) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        if (userId == null) {
            editor.remove(USER_ID_KEY);
        } else {
            editor.putString(USER_ID_KEY, userId);
        }
        return editor.commit();
    }

    public static String getIsSubscribed(Context context, boolean nullable) throws NullValueException {
        String data = getSharedPreferences(context).getString(IS_SUBSCRIBED_KEY, null);
        if (nullable != true && data == null) throw new NullValueException("isSubscribed");
        return data;
    }

    public static boolean setIsSubscribed(Context context, boolean isSubscribed) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(IS_SUBSCRIBED_KEY, String.valueOf(isSubscribed));
        return editor.commit();
    }

    public static boolean getAlreadyPermissionAsked(Context context) {
        boolean data = getSharedPreferences(context).getBoolean(ALREADY_PERMISSION_ASKED_KEY, false);
        return data;
    }

    public static boolean setAlreadyPermissionAsked(Context context, boolean asked) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putBoolean(ALREADY_PERMISSION_ASKED_KEY, asked);
        return editor.commit();
    }
}

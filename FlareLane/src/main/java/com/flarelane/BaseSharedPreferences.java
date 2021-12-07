package com.flarelane;

import android.content.Context;
import android.content.pm.PackageInfo;

class BaseSharedPreferences {
    private static final String SHARED_PREFERENCE_KEY_PREFIX = "com.flarelane.SHARED_PREFERENCE_KEY_";
    private static final String DEVICE_ID_KEY = "com.flarelane.DEVICE_ID_KEY";
    private static final String PROJECT_ID_KEY = "com.flarelane.PROJECT_ID_KEY";
    private static final String PUSH_TOKEN_KEY = "com.flarelane.PUSH_TOKEN_KEY";

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

    public static boolean setPushToken(Context context, String projectId) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(PUSH_TOKEN_KEY, projectId);
        return editor.commit();
    }
}

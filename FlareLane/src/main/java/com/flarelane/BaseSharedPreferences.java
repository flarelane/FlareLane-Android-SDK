package com.flarelane;

import android.content.Context;

class BaseSharedPreferences {
    private static final String SHARED_PREFERENCE_KEY = "com.flarelane.SHARED_PREFERENCE_KEY";
    private static final String DEVICE_ID_KEY = "com.flarelane.DEVICE_ID_KEY";
    private static final String PROJECT_ID_KEY = "com.flarelane.PROJECT_ID_KEY";
    private static final String PUSH_TOKEN_KEY = "com.flarelane.PUSH_TOKEN_KEY";

    private static android.content.SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCE_KEY, Context.MODE_PRIVATE);
    }

    public static String getDeviceId(Context context, boolean nullable) throws NullValueException {
        String data = getSharedPreferences(context).getString(DEVICE_ID_KEY, null);
        if (nullable == false && data == null) throw new NullValueException("deviceId");
        return data;
    }

    public static boolean setDeviceId(Context context, String deviceId) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(DEVICE_ID_KEY, deviceId);
        return editor.commit();
    }

    public static String getProjectId(Context context, boolean nullable) throws NullValueException {
        String data = getSharedPreferences(context).getString(PROJECT_ID_KEY, null);
        if (nullable == false && data == null) throw new NullValueException("projectId");
        return data;
    }

    public static boolean setProjectId(Context context, String projectId) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(PROJECT_ID_KEY, projectId);
        return editor.commit();
    }

    public static String getPushToken(Context context, boolean nullable) throws NullValueException {
        String data = getSharedPreferences(context).getString(PUSH_TOKEN_KEY, null);
        if (nullable == false && data == null) throw new NullValueException("pushToken");
        return data;
    }

    public static boolean setPushToken(Context context, String projectId) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(PUSH_TOKEN_KEY, projectId);
        return editor.commit();
    }
}

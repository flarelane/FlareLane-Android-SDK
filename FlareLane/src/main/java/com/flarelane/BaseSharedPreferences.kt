package com.flarelane

import android.content.Context
import android.content.SharedPreferences

internal object BaseSharedPreferences {
    private const val SHARED_PREFERENCE_KEY_PREFIX = "com.flarelane.SHARED_PREFERENCE_KEY_"
    private const val DEVICE_ID_KEY = "com.flarelane.DEVICE_ID_KEY"
    private const val PROJECT_ID_KEY = "com.flarelane.PROJECT_ID_KEY"
    private const val PUSH_TOKEN_KEY = "com.flarelane.PUSH_TOKEN_KEY"
    private const val USER_ID_KEY = "com.flarelane.USER_ID_KEY"
    private const val IS_SUBSCRIBED_KEY = "com.flarelane.IS_SUBSCRIBED_KEY"
    private const val ALREADY_PERMISSION_ASKED_KEY = "com.flarelane.ALREADY_PERMISSION_ASKED_KEY"

    private fun getSharedPreferencesKey(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            SHARED_PREFERENCE_KEY_PREFIX + info.firstInstallTime
        } catch (e: Exception) {
            SHARED_PREFERENCE_KEY_PREFIX
        }
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(getSharedPreferencesKey(context), Context.MODE_PRIVATE)
    }

    @JvmStatic
    @Throws(NullValueException::class)
    fun getDeviceId(context: Context, nullable: Boolean): String? {
        val data = getSharedPreferences(context).getString(DEVICE_ID_KEY, null)
        if (!nullable && data == null) throw NullValueException("deviceId")
        return data
    }

    @JvmStatic
    fun setDeviceId(context: Context, deviceId: String?): Boolean {
        val editor = getSharedPreferences(context).edit()
        editor.putString(DEVICE_ID_KEY, deviceId)
        return editor.commit()
    }

    @JvmStatic
    @Throws(NullValueException::class)
    fun getProjectId(context: Context, nullable: Boolean): String? {
        val data = getSharedPreferences(context).getString(PROJECT_ID_KEY, null)
        if (!nullable && data == null) throw NullValueException("projectId")
        return data
    }

    @JvmStatic
    fun setProjectId(context: Context, projectId: String?): Boolean {
        val editor = getSharedPreferences(context).edit()
        editor.putString(PROJECT_ID_KEY, projectId)
        return editor.commit()
    }

    @Throws(NullValueException::class)
    fun getPushToken(context: Context, nullable: Boolean): String? {
        val data = getSharedPreferences(context).getString(PUSH_TOKEN_KEY, null)
        if (!nullable && data == null) throw NullValueException("pushToken")
        return data
    }

    fun setPushToken(context: Context, pushToken: String?): Boolean {
        val editor = getSharedPreferences(context).edit()
        editor.putString(PUSH_TOKEN_KEY, pushToken)
        return editor.commit()
    }

    @JvmStatic
    @Throws(NullValueException::class)
    fun getUserId(context: Context, nullable: Boolean): String? {
        val data = getSharedPreferences(context).getString(USER_ID_KEY, null)
        if (!nullable && data == null) throw NullValueException("userId")
        return data
    }

    @JvmStatic
    fun setUserId(context: Context, userId: String?): Boolean {
        val editor = getSharedPreferences(context).edit()
        if (userId == null) {
            editor.remove(USER_ID_KEY)
        } else {
            editor.putString(USER_ID_KEY, userId)
        }
        return editor.commit()
    }

    @JvmStatic
    @Throws(NullValueException::class)
    fun getIsSubscribed(context: Context, nullable: Boolean): String? {
        val data = getSharedPreferences(context).getString(IS_SUBSCRIBED_KEY, null)
        if (!nullable && data == null) throw NullValueException("isSubscribed")
        return data
    }

    @JvmStatic
    fun setIsSubscribed(context: Context, isSubscribed: Boolean): Boolean {
        val editor = getSharedPreferences(context).edit()
        editor.putString(IS_SUBSCRIBED_KEY, isSubscribed.toString())
        return editor.commit()
    }

    @JvmStatic
    fun getAlreadyPermissionAsked(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(ALREADY_PERMISSION_ASKED_KEY, false)
    }

    @JvmStatic
    fun setAlreadyPermissionAsked(context: Context, asked: Boolean): Boolean {
        val editor = getSharedPreferences(context).edit()
        editor.putBoolean(ALREADY_PERMISSION_ASKED_KEY, asked)
        return editor.commit()
    }
}

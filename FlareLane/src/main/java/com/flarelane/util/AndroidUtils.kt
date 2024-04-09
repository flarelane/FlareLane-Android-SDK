package com.flarelane.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.flarelane.Logger

object AndroidUtils {
    fun appInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = activityManager.runningAppProcesses ?: return false
        for (runningAppProcess in runningAppProcesses) {
            if (runningAppProcess.processName == context.packageName &&
                runningAppProcess.importance ==
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            ) {
                return true
            }
        }
        return false
    }

    internal fun getManifestMetaBoolean(
        context: Context,
        name: String,
        defaultValue: Boolean = false
    ): Boolean {
        return getManifestMetaBundle(context)?.getBoolean(name) ?: defaultValue
    }

    private fun getManifestMetaBundle(context: Context): Bundle? {
        return try {
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            ).metaData
        } catch (e: Exception) {
            Logger.error("application info not found, $e")
            null
        }
    }

    @SuppressLint("DiscouragedApi")
    internal fun getResourceColor(
        context: Context,
        name: String,
        defaultColor: Int? = null
    ): Int? {
        val id = context.resources.getIdentifier(name, "color", context.packageName)
        return if (id != 0) {
            ContextCompat.getColor(context, id)
        } else {
            defaultColor
        }
    }

    @SuppressLint("DiscouragedApi")
    internal fun getResourceString(
        context: Context,
        name: String,
        defaultString: String? = null
    ): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id != 0) {
            context.getString(id)
        } else {
            defaultString
        }
    }

    @SuppressLint("DiscouragedApi")
    internal fun getResourceDrawableId(
        context: Context,
        name: String,
        defaultId: Int? = null
    ): Int? {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (id != 0) {
            id
        } else {
            defaultId
        }
    }
}

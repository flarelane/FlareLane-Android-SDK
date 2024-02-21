package com.flarelane.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.flarelane.Logger

object AndroidUtils {
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
}

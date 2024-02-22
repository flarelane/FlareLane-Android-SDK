package com.flarelane.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
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

    @SuppressLint("DiscouragedApi")
    internal fun getResourceColor(context: Context, name: String): Int? {
        val id = context.resources.getIdentifier(name, "color", context.packageName)
        return if (id != 0) {
             ContextCompat.getColor(context, id)
        } else {
            null
        }
    }
}

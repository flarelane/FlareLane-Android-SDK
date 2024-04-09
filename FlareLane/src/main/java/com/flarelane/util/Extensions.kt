package com.flarelane.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import com.flarelane.Logger
import org.json.JSONException
import org.json.JSONObject

internal fun String.toJSONObject(run: (JSONObject) -> Unit) {
    var jsonObject: JSONObject? = null
    try {
        jsonObject = JSONObject(this)
    } catch (e: JSONException) {
        Logger.error("toJSONObject() error, e=$e")
    }
    jsonObject?.let {
        run.invoke(it)
    }
}

internal fun String?.toJSONObjectWithNull(run: (JSONObject?) -> Unit) {
    val jsonObject: JSONObject?
    try {
        jsonObject = this?.let { JSONObject(it) }
    } catch (e: JSONException) {
        Logger.error("toJSONObjectWithNull() error, e=$e")
        return
    }
    run.invoke(jsonObject)
}

internal fun Intent.putParcelableDataClass(clazz: Parcelable) {
    putExtra(clazz::class.java.simpleName, clazz)
}

internal fun <T : Parcelable> Intent.getParcelableDataClass(clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(clazz.simpleName, clazz)
    } else {
        getParcelableExtra(clazz.simpleName)
    }
}

internal fun JSONObject?.getStringOrNull(key: String?): String? {
    val result = this?.optString(key)
    return when {
        result.isNullOrEmpty() -> null
        else -> result
    }
}

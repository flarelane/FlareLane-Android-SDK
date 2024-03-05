package com.flarelane.util

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Parcelable
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
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

@SuppressLint("RequiresFeature")
internal fun WebView.setAlgorithmicDarkeningAllow() {
    try {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
    } catch (_: Exception) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> {
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                    }

                    Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                    }
                }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                    settings,
                    WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                )
            }
        } catch (_: Exception) {
        }
    }
}

internal fun Intent.putParcelableDataClass(clazz: Parcelable) {
    putExtra(clazz::class.java.simpleName, clazz)
}

internal fun <T: Parcelable> Intent.getParcelableDataClass(clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(clazz.simpleName, clazz)
    } else {
        getParcelableExtra(clazz.simpleName)
    }
}

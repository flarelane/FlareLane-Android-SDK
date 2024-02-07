package com.flarelane.util

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.flarelane.Logger
import org.json.JSONException
import org.json.JSONObject

fun String.toJSONObject(run: (JSONObject) -> Unit) {
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

fun String?.toJSONObjectWithNull(run: (JSONObject?) -> Unit) {
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
fun WebView.setAlgorithmicDarkeningAllow() {
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

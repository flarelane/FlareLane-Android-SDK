package com.flarelane.util

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Parcelable
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.flarelane.Logger
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

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
    if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_NO) {
        return
    }
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

internal fun Int.dpToPx() = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this.toFloat(),
    Resources.getSystem().displayMetrics
).roundToInt()

internal fun Int.dpToPxF() = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this.toFloat(),
    Resources.getSystem().displayMetrics
)

internal fun Float.dpToPx() = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this,
    Resources.getSystem().displayMetrics
).roundToInt()

internal fun Float.dpToPxF() = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this,
    Resources.getSystem().displayMetrics
)

internal fun View.corner(value: Float, unit: Int = TypedValue.COMPLEX_UNIT_DIP) {
    clipToOutline = true
    background = gradientBackground().apply {
        cornerRadius = when (unit) {
            TypedValue.COMPLEX_UNIT_DIP -> value.dpToPxF()
            TypedValue.COMPLEX_UNIT_PX -> value
            else -> value
        }
    }
}

internal fun View.gradientBackground(): GradientDrawable {
    return if (background is GradientDrawable) {
        background as GradientDrawable
    } else {
        GradientDrawable().apply {
            if (background is ColorDrawable) {
                (background as ColorDrawable).let {
                    setColor(it.color)
                    alpha = it.alpha
                }
            }
        }
    }
}

package com.flarelane.webview.jsinterface

import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.flarelane.Constants
import com.flarelane.FlareLane
import com.flarelane.Logger
import com.flarelane.util.toJSONObject
import com.flarelane.util.toJSONObjectWithNull
import org.json.JSONObject

class FlareLaneJavascriptInterface(private val context: Context, private val webView: WebView) {

    @JavascriptInterface
    fun syncDeviceData() {
        val data: Map<String, Any> = mapOf(
            "projectId" to FlareLane.getProjectId(context),
            "platform" to Constants.SDK_PLATFORM,
            "deviceId" to FlareLane.getDeviceId(context),
            "userId" to FlareLane.getUserId(context)
        )
        val jsonString = JSONObject(data).toString()
        var script = "FlareLane.syncDeviceDataCallback($jsonString)"

        Logger.verbose("executed syncDeviceData from webView: $script")

        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    @JavascriptInterface
    fun setUserId(userId: String?) {
        FlareLane.setUserId(context, userId)
    }

    @JavascriptInterface
    fun setTags(jsonString: String) {
        jsonString.toJSONObject {
            FlareLane.setTags(context, it)
        }
    }

    @JavascriptInterface
    fun trackEvent(type: String, jsonString: String?) {
        jsonString.toJSONObjectWithNull {
            FlareLane.trackEvent(context, type, it)
        }
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        if (context is Activity) {
            context.runOnUiThread {
                webView.loadUrl(url)
            }
        }
    }

    companion object {
        const val BRIDGE_NAME = "FlareLaneBridge"
    }
}

package com.flarelane.webview.jsinterface

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.flarelane.Constants
import com.flarelane.FlareLane
import com.flarelane.util.toJSONObject
import com.flarelane.util.toJSONObjectWithNull
import org.json.JSONObject

class FlareLaneJavascriptInterface(private val context: Context, private val webView: WebView) {

    @JavascriptInterface
    fun syncDeviceData() {
        val data: Map<String, Any> = mapOf(
            "platform" to Constants.SDK_PLATFORM,
            "deviceId" to FlareLane.getDeviceId(context),
            "userId" to FlareLane.getUserId(context)
        )
        val jsonString = JSONObject(data).toString()

        webView.post {
            webView.evaluateJavascript("FlareLane.syncDeviceDataCallback($jsonString)", null)
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

    companion object {
        const val BRIDGE_NAME = "FlareLaneBridge"
    }
}

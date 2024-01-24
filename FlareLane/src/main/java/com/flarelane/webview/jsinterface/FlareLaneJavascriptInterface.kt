package com.flarelane.webview.jsinterface

import android.content.Context
import android.webkit.JavascriptInterface
import com.flarelane.FlareLane
import org.json.JSONObject

class FlareLaneJavascriptInterface(private val context: Context) {

    @JavascriptInterface
    fun setUserId(userId: String) {
        FlareLane.setUserId(context, userId)
    }

    @JavascriptInterface
    fun setTags(jsonString: String) {
        FlareLane.setTags(context, JSONObject(jsonString))
    }

    @JavascriptInterface
    fun trackEvent(type: String, jsonString: String) {
        FlareLane.trackEvent(context, type, JSONObject(jsonString))
    }
}

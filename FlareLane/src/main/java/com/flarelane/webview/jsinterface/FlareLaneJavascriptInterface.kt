package com.flarelane.webview.jsinterface

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.flarelane.FlareLane
import org.json.JSONObject

class FlareLaneJavascriptInterface(private val webView: WebView) {

    private val context = webView.context

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

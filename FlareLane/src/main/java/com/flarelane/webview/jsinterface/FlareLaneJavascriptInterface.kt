package com.flarelane.webview.jsinterface

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
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

    companion object {
        private const val BRIDGE_NAME = "AosBridge"

        @SuppressLint("AddJavascriptInterface")
        fun bind(webView: WebView) {
            webView.addJavascriptInterface(
                FlareLaneJavascriptInterface(webView.context),
                BRIDGE_NAME
            )
        }
    }
}

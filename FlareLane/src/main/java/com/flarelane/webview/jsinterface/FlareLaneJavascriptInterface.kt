package com.flarelane.webview.jsinterface

import android.content.Context
import android.webkit.JavascriptInterface
import com.flarelane.FlareLane
import com.flarelane.util.toJSONObject
import com.flarelane.util.toJSONObjectWithNull

class FlareLaneJavascriptInterface(private val context: Context) {

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

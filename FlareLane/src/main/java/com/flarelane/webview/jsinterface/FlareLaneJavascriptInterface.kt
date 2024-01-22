package com.flarelane.webview.jsinterface

import android.content.Context
import android.webkit.JavascriptInterface
import com.flarelane.FlareLane
import com.flarelane.Logger
import com.flarelane.util.toJSONObjectOrNull

class FlareLaneJavascriptInterface(private val context: Context) {

    @JavascriptInterface
    fun setUserId(userId: String?) {
        Logger.verbose("setUserId :: userId=$userId")
        FlareLane.setUserId(context, userId)
    }

    @JavascriptInterface
    fun setTags(jsonString: String) {
        Logger.verbose("setTags :: jsonString=$jsonString")
        jsonString.toJSONObjectOrNull()?.let {
            FlareLane.setTags(context, it)
        }
    }

    @JavascriptInterface
    fun trackEvent(type: String, jsonString: String?) {
        Logger.verbose("trackEvent :: type=$type, jsonString=$jsonString")
        FlareLane.trackEvent(context, type, jsonString.toJSONObjectOrNull())
    }

    companion object {
        const val BRIDGE_NAME = "FlareLaneBridge"
    }
}

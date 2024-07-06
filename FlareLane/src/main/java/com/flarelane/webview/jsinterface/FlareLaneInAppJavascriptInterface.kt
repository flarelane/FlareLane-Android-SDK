package com.flarelane.webview.jsinterface

import android.content.Context
import android.webkit.JavascriptInterface
import com.flarelane.EventService
import com.flarelane.FlareLane
import com.flarelane.InAppMessage
import com.flarelane.util.toJSONObject
import com.flarelane.util.toJSONObjectWithNull

class FlareLaneInAppJavascriptInterface(
    private val context: Context,
    private val messageId: String,
    private val listener: Listener
) {
    interface Listener {
        fun requestPushPermission(fallbackToSettings: Boolean)
        fun onOpenUrl(url: String)
        fun onClose()
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
    fun requestPushPermission(fallbackToSettings: Boolean = true) {
        listener.requestPushPermission(fallbackToSettings)
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        listener.onOpenUrl(url)
    }

    @JavascriptInterface
    fun executeAction(actionId: String) {
        var iam = InAppMessage(messageId)

        EventService.executeInAppMessageAction(
            context,
            iam,
            actionId
        )
    }

    @JavascriptInterface
    fun close() {
        listener.onClose()
    }

    companion object {
        const val BRIDGE_NAME = "FlareLaneIAMBridge"
    }
}

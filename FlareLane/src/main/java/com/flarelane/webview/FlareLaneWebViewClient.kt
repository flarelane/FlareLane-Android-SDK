package com.flarelane.webview

import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.flarelane.util.IntentUtil

internal open class FlareLaneWebViewClient(private val context: Context) : WebViewClient() {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return IntentUtil.createIntentIfResolveActivity(context, request.url)?.let {
            startActivity(context, it)
        } ?: false
    }

    @Deprecated(
        "Deprecated in Java", ReplaceWith(
            "super.shouldOverrideUrlLoading(view, url)",
            "android.webkit.WebViewClient"
        )
    )
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return IntentUtil.createIntentIfResolveActivity(context, url)?.let {
            startActivity(context, it)
        } ?: false
    }

    private fun startActivity(context: Context, intent: Intent) = try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

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
    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return IntentUtil.createIntentIfResolveActivity(context, request.url)?.let {
            startActivity(context, it)
            true
        } ?: false
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return IntentUtil.createIntentIfResolveActivity(context, url)?.let {
            startActivity(context, it)
            true
        } ?: false
    }

    private fun startActivity(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}

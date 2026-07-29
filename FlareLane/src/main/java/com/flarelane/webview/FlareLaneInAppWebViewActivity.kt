package com.flarelane.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import com.flarelane.FlareLane
import com.flarelane.InAppService
import com.flarelane.Logger
import com.flarelane.R
import com.flarelane.util.IntentUtil
import com.flarelane.webview.jsinterface.FlareLaneInAppJavascriptInterface


internal class FlareLaneInAppWebViewActivity : Activity(),
    FlareLaneInAppJavascriptInterface.Listener {
    // Owned by InAppMessagePresenter, which created and loaded it before this
    // Activity was started.
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = InAppMessagePresenter.consumeWebView(this)
        if (webView == null) {
            Logger.error("There is no preloaded IAM to display.")
            finish()
            return
        }
        this.webView = webView

        setContentView(R.layout.activity_inapp_webview)

        val container = findViewById<FrameLayout>(R.id.web_view_container)
        (webView.parent as? ViewGroup)?.removeView(webView)
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        // The html is already loaded, so there is nothing left to wait for.
        webView.visibility = View.VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        val webView = this.webView
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        if (webView?.originalUrl.isNullOrEmpty()) {
            super.finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        InAppMessagePresenter.release()
        webView = null
    }

    override fun finish() {
        InAppService.isDisplaying = false

        super.finish()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0,0)
        }
    }

    override fun requestPushPermission(fallbackToSettings: Boolean) {
        FlareLane.subscribe(this, fallbackToSettings, null)
    }

    override fun onOpenUrl(url: String) {
        try {
            Uri.parse(url)?.let {
                IntentUtil.createIntentIfResolveActivity(this, it)?.let { intent ->
                    try {
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                    } catch (_: Exception) {
                        // 외부 앱을 실행할 수 없음
                    }
                } ?: FlareLaneWebViewActivity.show(this, url)
            }
        } catch (_: Exception) {
            // 잘못된 url
        }
    }

    override fun onClose() {
        finish()
    }

    companion object {
        fun show(context: Context) {
            context.startActivity(
                Intent(context, FlareLaneInAppWebViewActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }
    }
}

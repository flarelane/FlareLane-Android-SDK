package com.flarelane.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.flarelane.FlareLane
import com.flarelane.InAppService
import com.flarelane.R
import com.flarelane.model.ModelInAppMessage
import com.flarelane.util.IntentUtil
import com.flarelane.webview.jsinterface.FlareLaneInAppJavascriptInterface
import com.flarelane.webview.jsinterface.FlareLaneJavascriptInterface

internal class FlareLaneInAppWebViewActivity : Activity(),
    FlareLaneInAppJavascriptInterface.Listener {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = if (intent.hasExtra(ID)) {
            intent.getStringExtra(ID)
        } else {
            null
        }

        val htmlString = if (intent.hasExtra(HTML_STRING)) {
            intent.getStringExtra(HTML_STRING)
        } else {
            null
        }

        if (id.isNullOrEmpty() || htmlString.isNullOrEmpty()) {
            finish()
            return
        }

        setContentView(R.layout.activity_inapp_webview)

        webView = findViewById(R.id.web_view)
        webView.visibility = View.INVISIBLE

        with(webView) {
            webChromeClient = flWebChromeClient
            webViewClient = flWebViewClient
            addJavascriptInterface(
                FlareLaneInAppJavascriptInterface(
                    this@FlareLaneInAppWebViewActivity,
                    id,
                    this@FlareLaneInAppWebViewActivity
                ),
                FlareLaneInAppJavascriptInterface.BRIDGE_NAME
            )
            webView.setBackgroundColor(Color.TRANSPARENT)
        }

        with(webView.settings) {
            cacheMode = WebSettings.LOAD_NO_CACHE
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = true
        }

//        webView.loadDataWithBaseURL(null, htmlString, "text/html; charset=utf-8", "utf-8", null)
        webView.loadUrl("https://minhyeok4dev.github.io/inapp4.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        if (webView.originalUrl.isNullOrEmpty()) {
            super.finish()
        }
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
                        intent.flags =
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
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

    private val flWebChromeClient = object : WebChromeClient() {
        override fun getDefaultVideoPoster(): Bitmap {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
        }
    }

    private val flWebViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            webView.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val ID = "id"
        private const val HTML_STRING = "html_string"

        fun show(context: Context, modelInAppMessage: ModelInAppMessage) {
            context.startActivity(
                Intent(context, FlareLaneInAppWebViewActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    it.putExtra(ID, modelInAppMessage.id)
                    it.putExtra(HTML_STRING, modelInAppMessage.htmlString)
                }
            )
        }
    }
}

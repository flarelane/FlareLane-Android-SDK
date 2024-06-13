package com.flarelane.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.flarelane.Logger
import com.flarelane.R
import com.flarelane.util.IntentUtil
import com.flarelane.webview.jsinterface.FlareLaneInAppJavascriptInterface
import com.flarelane.webview.jsinterface.FlareLaneJavascriptInterface

internal class FlareLaneInAppWebViewActivity : AppCompatActivity(),
    FlareLaneInAppJavascriptInterface.Listener {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loadUrl = if (intent.hasExtra(LOAD_URL)) {
            intent.getStringExtra(LOAD_URL)
        } else {
            null
        }

        if (loadUrl.isNullOrEmpty()) {
            finish()
            return
        }

        setContentView(R.layout.activity_inapp_webview)

        webView = findViewById(R.id.web_view)

        with(webView) {
            webChromeClient = flWebChromeClient
            addJavascriptInterface(
                FlareLaneJavascriptInterface(this@FlareLaneInAppWebViewActivity),
                FlareLaneJavascriptInterface.BRIDGE_NAME
            )
        }

        with(webView.settings) {
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.loadUrl(loadUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()
        if (webView.originalUrl.isNullOrEmpty()) {
            super.finish()
        }
    }

    override fun onOpenUrl(url: String) {
        try {
            Uri.parse(url)?.let {
                IntentUtil.createIntentIfResolveActivity(this, it)?.let { intent ->
                    try {
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
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

    companion object {
        private const val LOAD_URL = "load_url"

        internal fun show(context: Context, url: String) {
            context.startActivity(
                Intent(context, FlareLaneInAppWebViewActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    it.putExtra(LOAD_URL, url)
                }
            )
        }
    }
}

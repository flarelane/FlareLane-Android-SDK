package com.flarelane.webview.jsinterface

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.flarelane.R

internal class FlareLaneWebViewActivity: AppCompatActivity() {

    @SuppressLint("MissingInflatedId", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        // TODO url 검증?

        val loadUrl = if (intent.hasExtra(LOAD_URL)) {
            intent.getStringExtra(LOAD_URL)
        } else {
            null
        }

        if (loadUrl == null) {
            finish()
            return
        }

        findViewById<ImageButton>(R.id.iv_close).let {
            it.setOnClickListener {
                finish()
            }
        }

        val webView = findViewById<WebView>(R.id.web_view)
        with(webView) {
            // TODO settings 정의하기
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    view.loadUrl(loadUrl)
                    return true
                }
            }
        }
        webView.loadUrl(loadUrl)
    }

    companion object {
        const val LOAD_URL = "load_url"

        fun show() {
            // TODO
        }
    }
}

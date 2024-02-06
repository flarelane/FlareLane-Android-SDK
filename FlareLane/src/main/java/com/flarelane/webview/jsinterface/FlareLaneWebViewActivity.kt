package com.flarelane.webview.jsinterface

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.flarelane.R
import com.flarelane.util.setAlgorithmicDarkeningAllow

internal class FlareLaneWebViewActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar

    @SuppressLint("MissingInflatedId", "SetJavaScriptEnabled", "RequiresFeature")
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

        setContentView(R.layout.activity_webview)
        findViewById<Toolbar>(R.id.toolbar).let {
            it.setNavigationOnClickListener {
                finish()
            }
        }

        val webView = findViewById<WebView>(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        with(webView) {
            setAlgorithmicDarkeningAllow()
            webChromeClient = flWebChromeClient
            webViewClient = flWebViewClient
        }

        with(webView.settings) {
            cacheMode = WebSettings.LOAD_NO_CACHE
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            allowFileAccess = false
            displayZoomControls = false
        }

        println(webView.settings.userAgentString)
        webView.loadUrl(loadUrl)
    }

    override fun finish() {
        if (isTaskRoot) {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(it)
            }
        }
        super.finish()
    }

    private val flWebChromeClient by lazy {
        object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
            }
        }
    }

    private val flWebViewClient by lazy {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val LOAD_URL = "load_url"

        internal fun show(context: Context, url: String) {
            context.startActivity(
                Intent(context, FlareLaneWebViewActivity::class.java).also {
                    it.putExtra(LOAD_URL, url)
                }
            )
        }
    }
}

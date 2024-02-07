package com.flarelane.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.browser.customtabs.CustomTabsIntent
import com.flarelane.Logger
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
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            allowFileAccess = true
            databaseEnabled = true
            useWideViewPort = true
            displayZoomControls = false
            defaultTextEncodingName = "UTF-8"
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
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return shouldOverrideUrlLoading(request.url)
            }

            @Deprecated(
                "Deprecated in Java", ReplaceWith(
                    "super.shouldOverrideUrlLoading(view, url)",
                    "android.webkit.WebViewClient"
                )
            )
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return try {
                    val uri = Uri.parse(url)
                    shouldOverrideUrlLoading(uri)
                } catch (e: Exception) {
                    Logger.error("WebView error, e=$e")
                    false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }

            private fun shouldOverrideUrlLoading(url: Uri) =
                when (url.scheme) {
                    null, "http", "https" -> false
                    else -> {
                        CustomTabsIntent.Builder().build().launchUrl(
                            this@FlareLaneWebViewActivity,
                            url
                        )
                        true
                    }
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

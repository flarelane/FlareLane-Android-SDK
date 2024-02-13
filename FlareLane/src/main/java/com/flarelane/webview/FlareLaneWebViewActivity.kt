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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.flarelane.Logger
import com.flarelane.R
import com.flarelane.util.setAlgorithmicDarkeningAllow

internal class FlareLaneWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
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

        webView = findViewById(R.id.web_view)
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
            databaseEnabled = true
            useWideViewPort = true
            allowFileAccess = false
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

    private val flWebChromeClient = object : WebChromeClient() {
        override fun getDefaultVideoPoster(): Bitmap {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
        }
    }

    private val flWebViewClient = object : WebViewClient() {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
//                return shouldOverrideUrlLoading(view, request.url.toString())
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
                Logger.error("FlareLaneWebView url error, url=$url, e=$e")
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

        private fun shouldOverrideUrlLoading(url: Uri): Boolean {
            Logger.error("shouldOverrideUrlLoading :: url=$url")
            return try {
                when (UrlSchemes.of(url.scheme)) {
                    UrlSchemes.HTTP, UrlSchemes.HTTPS -> {
                        if (url.toString().contains("play.google.com/store/apps/details")) {
                            url.getQueryParameter("id")?.let { packageName ->
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=$packageName")
                                    ).also { i ->
                                        i.`package` = "com.android.vending"
                                    }
                                )
                                Toast.makeText(
                                    this@FlareLaneWebViewActivity,
                                    "[play store]=$url",
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            } ?: run {
                                false
                            }
                        } else {
                            false
                        }
                    }

                    UrlSchemes.TEL -> {
                        Toast.makeText(
                            this@FlareLaneWebViewActivity,
                            "[tel]=$url",
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(Intent.ACTION_DIAL, url))
                        true
                    }

                    UrlSchemes.MAIL_TO -> {
                        Toast.makeText(
                            this@FlareLaneWebViewActivity,
                            "[mail]=$url",
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(Intent.ACTION_SENDTO, url))
                        true
                    }

                    UrlSchemes.INTENT -> {
                        Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME).let {
                            it.`package`?.let { packageName ->
                                val existPackage =
                                    packageManager.getLaunchIntentForPackage(packageName)
                                if (existPackage != null) {
                                    startActivity(it)
                                    Toast.makeText(
                                        this@FlareLaneWebViewActivity,
                                        "[intent]=$url",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    throw Exception()
                                }
                            } ?: run {
                                throw Exception()
                            }
                        }
                        true
                    }

                    UrlSchemes.MARKET -> {
                        Toast.makeText(
                            this@FlareLaneWebViewActivity,
                            "[market]=$url",
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        true
                    }

                    UrlSchemes.DATA -> {
                        throw Exception()
                    }

                    UrlSchemes.CUSTOM -> {
                        Toast.makeText(
                            this@FlareLaneWebViewActivity,
                            "[custom]=$url",
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        if (webView.canGoBack()) {
                            webView.goBack()
                        }
                        true
                    }

                    null -> {
                        throw Exception()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(
                    this@FlareLaneWebViewActivity,
                    "해당 url 을 로드할 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                true
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

package com.flarelane.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.flarelane.Constants
import com.flarelane.R
import com.flarelane.util.AndroidUtils
import com.flarelane.util.setAlgorithmicDarkeningAllow
import com.flarelane.webview.jsinterface.FlareLaneJavascriptInterface
import com.google.android.material.appbar.AppBarLayout

internal class FlareLaneWebViewActivity : AppCompatActivity() {
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var ibBack: ImageButton
    private lateinit var tvUrl: TextView
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

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

        setContentView(R.layout.activity_webview)

        appBarLayout = findViewById(R.id.app_bar_layout)
        ibBack = findViewById(R.id.ib_back)
        tvUrl = findViewById(R.id.tv_url)
        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        ibBack.setOnClickListener {
            finish()
        }

        setTextUrlHost(loadUrl)

        with(webView) {
            setAlgorithmicDarkeningAllow()
            webChromeClient = flWebChromeClient
            webViewClient = flWebViewClient
            addJavascriptInterface(
                FlareLaneJavascriptInterface(this@FlareLaneWebViewActivity),
                FlareLaneJavascriptInterface.BRIDGE_NAME
            )
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

        setUiStyle()
    }

    override fun onStop() {
        super.onStop()
        if (webView.originalUrl.isNullOrEmpty()) {
            super.finish()
        }
    }

    override fun finish() {
        if (isTaskRoot) {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
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

    private val flWebViewClient = object : FlareLaneWebViewClient(this) {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            appBarLayout.setExpanded(true)
            if (!url.isNullOrEmpty()) {
                setTextUrlHost(url)
            }
            progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            progressBar.visibility = View.GONE
        }
    }

    private fun setTextUrlHost(url: String) {
        tvUrl.text = Uri.parse(url).host
    }

    private fun setUiStyle() {
        AndroidUtils.getResourceColor(this, Constants.COLOR_WEB_VIEW_APP_BAR_BACKGROUND)?.let {
            appBarLayout.setBackgroundColor(it)
        }
        AndroidUtils.getResourceColor(this, Constants.COLOR_WEB_VIEW_URL_TEXT_COLOR)?.let {
            tvUrl.setTextColor(it)
        }
        AndroidUtils.getResourceColor(this, Constants.COLOR_WEB_VIEW_CLOSE_ICON_COLOR)?.let {
            ibBack.colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
        }
        AndroidUtils.getResourceColor(this, Constants.COLOR_WEB_VIEW_DIVIDER_COLOR)?.let {
            findViewById<View>(R.id.web_view_divider).setBackgroundColor(it)
        }
        val layerDrawable = progressBar.progressDrawable as LayerDrawable
        layerDrawable.getDrawable(0).colorFilter = PorterDuffColorFilter(
            Color.TRANSPARENT, PorterDuff.Mode.SRC_IN
        )
        layerDrawable.getDrawable(1).colorFilter = PorterDuffColorFilter(
            Color.TRANSPARENT, PorterDuff.Mode.SRC_IN
        )
        AndroidUtils.getResourceColor(this, Constants.COLOR_WEB_VIEW_PROGRESS_BAR_COLOR)?.let {
            layerDrawable.getDrawable(2).colorFilter = PorterDuffColorFilter(
                it, PorterDuff.Mode.SRC_IN
            )
        }
    }

    companion object {
        private const val LOAD_URL = "load_url"

        internal fun show(context: Context, url: String) {
            context.startActivity(
                Intent(context, FlareLaneWebViewActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    it.putExtra(LOAD_URL, url)
                }
            )
        }
    }
}

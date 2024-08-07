package com.flarelane.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.flarelane.webview.jsinterface.FlareLaneJavascriptInterface

class WebViewTestActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_bridge_test)

        findViewById<Toolbar>(R.id.toolbar).let {
            setSupportActionBar(it)
            it.title = getString(R.string.app_name)
            it.setNavigationIcon(R.drawable.ic_left_arrow)
            it.setNavigationOnClickListener {
                finish()
            }
        }

        val webView: WebView = findViewById(R.id.web_view)
        with(webView) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            // add FlareLane javascript interface
            addJavascriptInterface(
                FlareLaneJavascriptInterface(this@WebViewTestActivity, webView),
                FlareLaneJavascriptInterface.BRIDGE_NAME
            )

            loadUrl("https://junyeongchoi.github.io/")
        }
    }

    companion object {
        private const val ASSET_FILE_PATH = "file:///android_asset/"
        private const val LOCAL_HTML_FILE = "webview_bridge_test.html"
    }
}

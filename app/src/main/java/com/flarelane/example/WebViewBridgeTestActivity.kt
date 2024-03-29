package com.flarelane.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.flarelane.webview.jsinterface.FlareLaneJavascriptInterface

class WebViewBridgeTestActivity : AppCompatActivity() {
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
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webChromeClient = WebChromeClient()

            // add FlareLane javascript interface
            addJavascriptInterface(
                FlareLaneJavascriptInterface(this@WebViewBridgeTestActivity),
                FlareLaneJavascriptInterface.BRIDGE_NAME
            )

            loadUrl(ASSET_FILE_PATH + LOCAL_HTML_FILE)
        }
    }

    companion object {
        private const val ASSET_FILE_PATH = "file:///android_asset/"
        private const val LOCAL_HTML_FILE = "webview_bridge_test.html"
    }
}

package com.flarelane.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.flarelane.Logger
import com.flarelane.model.ModelInAppMessage
import com.flarelane.webview.jsinterface.FlareLaneInAppJavascriptInterface

/**
 * Loads the message html into an off-window WebView and starts
 * [FlareLaneInAppWebViewActivity] only after the page has finished loading.
 *
 * The Activity is translucent and full-screen, so starting it before the html is
 * ready leaves an invisible window on top of the host app: it swallows touches and
 * pauses the underlying Activity for as long as the html's remote resources take to
 * download. Loading first and presenting afterwards matches the iOS SDK, which
 * creates its UIWindow in `didFinishNavigation`.
 *
 * All state here is confined to the main thread; bridge callbacks arriving on the
 * JavaBridge thread are posted back before touching it.
 */
internal object InAppMessagePresenter : FlareLaneInAppJavascriptInterface.Listener {

    // Upper bound on the invisible load phase. A single stalled font or image must
    // not sink the message entirely, so present with whatever has painted so far.
    private const val LOAD_TIMEOUT_MS = 5000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var pending: Pending? = null

    // Set once the Activity is on screen. Bridge callbacks are forwarded to it from
    // then on; before that only `onClose` is meaningful (html can close itself while
    // loading, e.g. from its own frequency-cap script).
    private var attachedListener: FlareLaneInAppJavascriptInterface.Listener? = null

    private class Pending(
        val appContext: Context,
        val message: ModelInAppMessage,
        val webView: WebView
    ) {
        var isSettled = false
        var timeoutRunnable: Runnable? = null
    }

    /**
     * Begins loading [message]. The Activity is started later, from [settle].
     * Safe to call from any thread.
     */
    fun present(context: Context, message: ModelInAppMessage) {
        val appContext = context.applicationContext

        mainHandler.post {
            if (pending != null) {
                Logger.verbose("IAM is already being presented.")
                return@post
            }

            try {
                val webView = createWebView(appContext, message)
                val current = Pending(appContext, message, webView)
                pending = current

                val timeoutRunnable = Runnable {
                    Logger.error("IAM load exceeded ${LOAD_TIMEOUT_MS}ms, displaying as-is.")
                    settle()
                }
                current.timeoutRunnable = timeoutRunnable
                mainHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS)

                webView.loadDataWithBaseURL(
                    null,
                    message.htmlString,
                    "text/html; charset=utf-8",
                    "utf-8",
                    null
                )
            } catch (e: Exception) {
                Logger.error("Failed to preload IAM.", e)
                discard()
            }
        }
    }

    /**
     * Hands the loaded WebView over to the Activity. Called by
     * [FlareLaneInAppWebViewActivity.onCreate]; returns null when there is nothing
     * to display, in which case the Activity finishes immediately.
     */
    fun consumeWebView(listener: FlareLaneInAppJavascriptInterface.Listener): WebView? {
        val current = pending ?: return null
        attachedListener = listener
        return current.webView
    }

    /** Called when the Activity is destroyed, so the WebView is not leaked. */
    fun release() {
        mainHandler.post { discard() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(appContext: Context, message: ModelInAppMessage): WebView {
        return WebView(appContext).apply {
            // Stays invisible until the Activity attaches it, so a partially painted
            // page is never shown.
            visibility = View.INVISIBLE
            setBackgroundColor(Color.TRANSPARENT)

            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    settle()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    // Sub-resource failures still leave a presentable message; only a
                    // failed document means there is nothing to show.
                    if (request?.isForMainFrame == true) {
                        Logger.error("Failed to load IAM document: ${error?.description}")
                        discard()
                    }
                }
            }

            addJavascriptInterface(
                FlareLaneInAppJavascriptInterface(
                    appContext,
                    message.id,
                    InAppMessagePresenter
                ),
                FlareLaneInAppJavascriptInterface.BRIDGE_NAME
            )

            with(settings) {
                cacheMode = WebSettings.LOAD_NO_CACHE
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = true
                javaScriptCanOpenWindowsAutomatically = true
            }
        }
    }

    /** The page is ready (or out of time): put it on screen exactly once. */
    private fun settle() {
        val current = pending ?: return
        if (current.isSettled) return
        current.isSettled = true

        current.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        current.timeoutRunnable = null

        FlareLaneInAppWebViewActivity.show(current.appContext)
    }

    private fun discard() {
        val current = pending ?: return
        current.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        pending = null
        attachedListener = null

        with(current.webView) {
            (parent as? ViewGroup)?.removeView(this)
            stopLoading()
            destroy()
        }
    }

    override fun requestPushPermission(fallbackToSettings: Boolean) {
        val listener = attachedListener
        if (listener == null) {
            Logger.verbose("requestPushPermission ignored: IAM is not displayed yet.")
            return
        }
        listener.requestPushPermission(fallbackToSettings)
    }

    override fun onOpenUrl(url: String) {
        val listener = attachedListener
        if (listener == null) {
            Logger.verbose("openUrl ignored: IAM is not displayed yet.")
            return
        }
        listener.onOpenUrl(url)
    }

    override fun onClose() {
        val listener = attachedListener
        if (listener != null) {
            listener.onClose()
            return
        }
        // The html closed itself while still loading, so it never becomes visible.
        Logger.verbose("IAM closed itself before display.")
        mainHandler.post { discard() }
    }
}

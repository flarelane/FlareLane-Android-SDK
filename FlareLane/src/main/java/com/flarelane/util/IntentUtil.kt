package com.flarelane.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri

internal object IntentUtil {
    @SuppressLint("QueryPermissionsNeeded")
    fun setPackageFromResolveInfoList(context: Context, intent: Intent) {
        context.packageManager.queryIntentActivities(intent, 0).forEach { resolveInfo ->
            if (resolveInfo.activityInfo.packageName == context.packageName) {
                intent.setPackage(context.packageName)
                return
            }
        }
    }

    fun createIntentIfResolveActivity(context: Context, url: String): Intent? {
        return createIntentIfResolveActivity(context, Uri.parse(url))
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun createIntentIfResolveActivity(context: Context, url: Uri): Intent? {
        var resultIntent: Intent? = null
        try {
            when (UrlScheme.of(url.scheme)) {
                UrlScheme.HTTP, UrlScheme.HTTPS -> {
                    if (url.toString().contains(PlayStoreInfo.PLAY_STORE_DETAIL_URL_PREFIX)) {
                        url.getQueryParameter("id")?.let { packageName ->
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("${PlayStoreInfo.PLAY_STORE_MARKET_URL_PREFIX}$packageName")
                            ).also { i ->
                                i.setPackage(PlayStoreInfo.PLAY_STORE_PACKAGE_NAME)
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                resultIntent = intent
                            }
                        }
                    }
                }

                UrlScheme.TEL -> {
                    resultIntent = Intent(Intent.ACTION_DIAL, url)
                }

                UrlScheme.MAIL_TO -> {
                    resultIntent = Intent(Intent.ACTION_SENDTO, url)
                }

                UrlScheme.INTENT -> {
                    resultIntent = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME).also { i ->
                        setPackageFromResolveInfoList(context, i)
                    }
                }

                UrlScheme.MARKET -> {
                    resultIntent = Intent(Intent.ACTION_VIEW, url)
                }

                UrlScheme.CUSTOM -> {
                    resultIntent = Intent(Intent.ACTION_VIEW, url).also { i ->
                        setPackageFromResolveInfoList(context, i)
                    }
                }

                UrlScheme.DATA, null -> {
                    // do nothing
                }
            }
        } catch (_: Exception) {
            resultIntent = null
        }

        return resultIntent
    }
}

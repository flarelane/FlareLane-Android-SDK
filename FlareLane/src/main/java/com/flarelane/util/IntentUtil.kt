package com.flarelane.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

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
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (intent.resolveActivity(context.packageManager) == null) {
                                resultIntent = intent
                            }
                        } else {
                            context.packageManager.queryIntentActivities(
                                intent,
                                PackageManager.GET_RESOLVED_FILTER
                            ).forEach run@{ resolveInfo ->
                                resolveInfo.filter.authoritiesIterator().forEach {
                                    if (it.host == url.host) {
                                        intent.setPackage(resolveInfo.activityInfo.packageName)
                                        resultIntent = intent
                                        return@run
                                    }
                                }
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
                    resultIntent = Intent.parseUri(
                        url.toString(), Intent.URI_INTENT_SCHEME
                    ).also { i ->
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

    /**
     * The host app's launcher intent shaped like what the home screen sends.
     *
     * `getLaunchIntentForPackage` keeps the package set on the returned intent while the launcher's
     * own intent has none. Up to Android 13 the system includes the package when it compares an
     * incoming intent with the task's root intent, so keeping it makes a running app get a SECOND
     * main activity stacked on its task instead of being resumed. Flags are left as returned
     * (NEW_TASK). Same recipe as OneSignal and Airship.
     */
    fun createLauncherIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply { setPackage(null) }
}

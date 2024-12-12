package com.flarelane

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.flarelane.util.AndroidUtils
import com.flarelane.util.IntentUtil
import com.flarelane.util.getParcelableDataClass
import com.flarelane.webview.FlareLaneWebViewActivity

internal class NotificationClickedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val notification = intent.getParcelableDataClass(Notification::class.java) ?: run {
                throw Exception("notification is null")
            }
            Logger.verbose("NotificationClickedActivity notification=$notification")

            val projectId = BaseSharedPreferences.getProjectId(this.applicationContext, false)
            val deviceId = BaseSharedPreferences.getDeviceId(this.applicationContext, false)
            val userId = BaseSharedPreferences.getUserId(this.applicationContext, true)

            EventService.createNotificationClicked(projectId, deviceId, notification, userId)
            handleNotificationClicked(notification)
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        } finally {
            finish()
        }
    }

    private fun handleNotificationClicked(notification: Notification) {
        if (notification.url.isNullOrEmpty()) {
            launchApp()
        } else {
            val isIgnoreLaunchUrl = AndroidUtils.getManifestMetaBoolean(
                this, Constants.DISMISS_LAUNCH_URL
            ) || notification.dataJsonObject?.optString(Constants.DISMISS_LAUNCH_URL) == "true"
            if (isIgnoreLaunchUrl) {
                Logger.verbose("Works natively without automatic URL processing")
                launchApp()
                return
            }

            try {
                val url = Uri.parse(notification.url)
                if (url.scheme == null) {
                    Logger.verbose("Url scheme is null. url=${notification.url}")
                    launchApp()
                    return
                }

                IntentUtil.createIntentIfResolveActivity(this, url)?.let {
                    try {
                        it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(it)
                    } catch (_: Exception) {
                        Logger.verbose("Url is not available. url=${notification.url}")
                        launchApp()
                    }
                } ?: FlareLaneWebViewActivity.show(this, notification.url)
            } catch (_: Exception) {
            }
        }
    }

    private fun launchApp() {
        if (isTaskRoot) {
            Logger.verbose("This is last activity in the stack")
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it)
            }
        }
    }
}

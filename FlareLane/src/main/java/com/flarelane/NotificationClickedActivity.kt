package com.flarelane

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.flarelane.notification.NotificationClickedButton
import com.flarelane.util.AndroidUtils
import com.flarelane.util.IntentUtil
import com.flarelane.util.getParcelableDataClass
import com.flarelane.webview.FlareLaneWebViewActivity

internal class NotificationClickedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Logger.verbose("NotificationClickedActivity onCreate")
            val notification = intent.getParcelableDataClass(Notification::class.java) ?: return
            val notificationClickedButton = intent.getParcelableDataClass(
                NotificationClickedButton::class.java
            )
            val projectId = BaseSharedPreferences.getProjectId(this.applicationContext, false)
            val deviceId = BaseSharedPreferences.getDeviceId(this.applicationContext, false)
            Logger.verbose("NotificationClickedActivity notification=$notification")
            // TODO button clicked event
            EventService.createClicked(projectId, deviceId, notification)
            handleNotificationClicked(notification)
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        } finally {
            finish()
        }
    }

    private fun handleNotificationClicked(
        notification: Notification,
        notificationClickedButton: NotificationClickedButton? = null
    ) {
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

            val url = notificationClickedButton?.link ?: notification.url
            IntentUtil.createIntentIfResolveActivity(this, url)?.let {
                try {
                    it.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(it)
                } catch (_: Exception) {
                    Logger.verbose("Url is not available. url=$url")
                    launchApp()
                }
            } ?: FlareLaneWebViewActivity.show(this, url)
        }
    }

    private fun launchApp() {
        if (isTaskRoot) {
            Logger.verbose("This is last activity in the stack")
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(it)
            }
        }
    }
}

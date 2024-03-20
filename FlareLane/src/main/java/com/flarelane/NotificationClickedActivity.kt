package com.flarelane

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.flarelane.notification.NotificationClickEvent
import com.flarelane.notification.NotificationClickedButton
import com.flarelane.util.AndroidUtils
import com.flarelane.util.IntentUtil
import com.flarelane.util.getParcelableDataClass
import com.flarelane.webview.FlareLaneWebViewActivity

internal class NotificationClickedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.verbose("NotificationClickedActivity onCreate")
        val notifyId = intent.getIntExtra("notifyId", -1).also {
            if (it == -1) {
                return
            }
        }
        try {
            val notification = intent.getParcelableDataClass(Notification::class.java) ?: return
            val notificationClickedButton = intent.getParcelableDataClass(
                NotificationClickedButton::class.java
            )
            val projectId = BaseSharedPreferences.getProjectId(this.applicationContext, false)
            val deviceId = BaseSharedPreferences.getDeviceId(this.applicationContext, false)

            val event = NotificationClickEvent(notification, notificationClickedButton)
            Logger.verbose("NotificationClickedActivity event=$event")

            handleNotificationClicked(event)

            if (notificationClickedButton == null) {
                EventService.createNotificationClicked(projectId, deviceId, event)
            } else {
                EventService.createNotificationButtonClicked(projectId, deviceId, event)
            }
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        } finally {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifyId)
            finish()
        }
    }

    private fun handleNotificationClicked(event: NotificationClickEvent) {
        val url = event.notificationClickedButton?.link ?: event.notification.url
        if (url.isNullOrEmpty()) {
            launchApp()
        } else {
            val isIgnoreLaunchUrl = AndroidUtils.getManifestMetaBoolean(
                this, Constants.DISMISS_LAUNCH_URL
            ) || event.notification.dataJsonObject?.optString(Constants.DISMISS_LAUNCH_URL) == "true"
            if (isIgnoreLaunchUrl) {
                Logger.verbose("Works natively without automatic URL processing")
                launchApp()
                return
            }

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

package com.flarelane

import android.app.Activity
import android.os.Bundle
import com.flarelane.util.AndroidUtils
import com.flarelane.util.IntentUtil
import com.flarelane.webview.FlareLaneWebViewActivity

internal class NotificationClickedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            if (intent.hasExtra("notificationId")) {
                val notificationId = intent.getStringExtra("notificationId")
                val projectId = BaseSharedPreferences.getProjectId(this.applicationContext, false)
                val deviceId = BaseSharedPreferences.getDeviceId(this.applicationContext, false)
                val notification = Notification(
                    notificationId!!,
                    intent.getStringExtra("body")!!,
                    intent.getStringExtra("data")!!,
                    intent.getStringExtra("title"),
                    intent.getStringExtra("url"),
                    intent.getStringExtra("imageUrl")
                )

                EventService.createClicked(projectId, deviceId, notification)

                handleNotificationClicked(notification)
            }
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
            if (AndroidUtils.getManifestMetaBoolean(this, Constants.META_NAME_IGNORE_LAUNCH_URLS)) {
                Logger.verbose("Works natively without automatic URL processing")
                launchApp()
                return
            }

            IntentUtil.createIntentIfResolveActivity(this, notification.url!!)?.let {
                try {
                    startActivity(it)
                } catch (_: Exception) {
                    FlareLaneWebViewActivity.show(this, notification.url!!)
                }
            } ?: FlareLaneWebViewActivity.show(this, notification.url!!)
        }
    }

    private fun launchApp() {
        if (isTaskRoot) {
            Logger.verbose("This is last activity in the stack")
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            startActivity(launchIntent)
        }
    }
}

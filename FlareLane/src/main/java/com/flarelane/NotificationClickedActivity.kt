package com.flarelane

import android.app.Activity
import android.os.Bundle
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
            if (isTaskRoot) {
                Logger.verbose("This is last activity in the stack")
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                startActivity(launchIntent)
            }
        } else {
            FlareLaneWebViewActivity.show(this, notification.url!!)
        }
    }
}
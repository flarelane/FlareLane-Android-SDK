package com.flarelane

import android.app.Activity
import android.os.Bundle
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
            val projectId = BaseSharedPreferences.getProjectId(this.applicationContext, false)
            val deviceId = BaseSharedPreferences.getDeviceId(this.applicationContext, false)
            Logger.verbose("NotificationClickedActivity notification=$notification")
            EventService.createClicked(projectId, deviceId, notification)
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
                this, Constants.META_NAME_IGNORE_LAUNCH_URLS
            ) || (notification.dataJsonObject?.has(Constants.REMOTE_IGNORE_LAUNCH_URL) == true &&
                    notification.dataJsonObject?.getBoolean(Constants.REMOTE_IGNORE_LAUNCH_URL) == true)
            if (isIgnoreLaunchUrl) {
                Logger.verbose("Works natively without automatic URL processing")
                launchApp()
                return
            }

            IntentUtil.createIntentIfResolveActivity(this, notification.url)?.let {
                try {
                    startActivity(it)
                } catch (_: Exception) {
                    FlareLaneWebViewActivity.show(this, notification.url)
                }
            } ?: FlareLaneWebViewActivity.show(this, notification.url)
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

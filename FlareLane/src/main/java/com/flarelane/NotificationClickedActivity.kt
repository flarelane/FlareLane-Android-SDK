package com.flarelane

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
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
            // The Parcelable already carries `clickedButtonIndex` when an action button was tapped
            // (set in NotificationReceivedEvent before building each per-button PendingIntent).
            // No separate Intent extra needed — one source of truth.
            val notification = intent.getParcelableDataClass(Notification::class.java) ?: run {
                throw Exception("notification is null")
            }
            Logger.verbose("NotificationClickedActivity notification=$notification")

            // Body taps are auto-dismissed by the builder's `setAutoCancel(true)`. Action-button
            // taps don't get that auto-dismiss, so the same button stays tappable until the user
            // swipes it away. Cancel explicitly for the button case only — body taps stay on the
            // master code path.
            //
            // Gate is `clickedButtonIndex` (not `clickedButton`) on purpose: this reflects
            // "did the OS report a button-slot tap", which is the system-level fact that
            // determines whether auto-dismiss already fired. Out-of-range / unresolvable
            // button data still needs manual dismissal because the OS still treats it as a
            // button tap — using `clickedButton != null` here would leak undismissed
            // notifications in that edge case.
            if (notification.clickedButtonIndex != null) {
                dismissSystemNotification(notification)
            }

            // Symmetric to NotificationReceivedEvent.display(): fire the CLICKED event and click
            // handler through the event wrapper, then handle the (Activity-scoped) deep link.
            NotificationClickedEvent(this.applicationContext, notification).process()
            handleNotificationClicked(notification)
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        } finally {
            finish()
        }
    }

    private fun dismissSystemNotification(notification: Notification) {
        try {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(notification.currentAndroidNotificationId())
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        }
    }

    private fun handleNotificationClicked(notification: Notification) {
        // `clickedUrl` already picks the right source — button.link for button clicks, body
        // url for body clicks, null when neither is set. No extra fallback needed here.
        val targetUrl = notification.clickedUrl
        if (targetUrl.isNullOrEmpty()) {
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
                val url = Uri.parse(targetUrl)
                if (url.scheme == null) {
                    Logger.verbose("Url scheme is null. url=$targetUrl")
                    launchApp()
                    return
                }

                IntentUtil.createIntentIfResolveActivity(this, url)?.let {
                    try {
                        it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(it)
                    } catch (_: Exception) {
                        Logger.verbose("Url is not available. url=$targetUrl")
                        launchApp()
                    }
                } ?: FlareLaneWebViewActivity.show(this, targetUrl)
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

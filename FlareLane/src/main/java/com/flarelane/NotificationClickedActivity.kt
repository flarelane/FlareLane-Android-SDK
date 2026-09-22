package com.flarelane

import android.app.Activity
import android.app.ActivityManager
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

            // The tap removed the child (autoCancel / explicit cancel above) — re-derive the
            // group summary so it collapses or disappears instead of lingering as a ghost.
            notification.threadId?.takeIf { it.isNotEmpty() }?.let { threadId ->
                NotificationGroupManager.refreshSummary(
                    applicationContext, threadId, notification.currentChannelId(applicationContext)
                )
            }

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
            // Chat-style pushes are posted under the stable conversation id instead (only when
            // the avatar download succeeded, which this side can't know) — cancel both
            // candidates; cancelling an absent id is a no-op.
            if (notification.communicationData != null) {
                manager?.cancel(notification.conversationNotificationId())
            }
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

    /**
     * Bring the app forward the way a Recents tap does: resume its existing task untouched (no new
     * activity instance, no clear-top, no onNewIntent), so whatever the click handler just navigated
     * to stays on top, and singleTask roots or tasks not rooted by the launcher are left alone.
     * Only when the app has no task at all (cold start, swiped away from Recents) fall back to the
     * launcher intent.
     *
     * There is intentionally no isTaskRoot guard here: this Activity is started from a notification
     * PendingIntent (no source activity, so NEW_TASK is forced) into its own task
     * (android:taskAffinity in the manifest), so it is ALWAYS the root of that task. isTaskRoot was
     * always true and said nothing about whether the host app was running. Do not re-add it.
     */
    private fun launchApp() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // MRU-ordered (index 0 = most recent): AOSP RecentTasks.getAppTasksList walks the
            // recents list, which re-inserts a task at index 0 on every resume / move-to-top.
            // The list is filtered only by uid and package, so this trampoline's own task is
            // in it too. Judge a task by what is on TOP of it, not by its root: a task whose top
            // is an SDK screen (this trampoline, or a stale SDK WebView left from an earlier push)
            // is not the host app's UI, so skip it and let the launcher fallback decide as a
            // home-screen tap would: a task rooted by the SDK WebView gets the main activity
            // stacked on it, a launcher-rooted task is brought forward as-is (WebView still on
            // top) — exactly what Android 14+ already did in 1.11.2. A task that was merely
            // rooted by an SDK WebView but now shows host activities is resumed.
            val appTask = activityManager.appTasks.firstOrNull { task ->
                // getTaskInfo() throws (it does not return null) when the task died between the
                // two calls; keep that per task so one stale entry cannot abort the whole lookup.
                val topClassName = runCatching { task.taskInfo.topActivity?.className }.getOrNull()
                topClassName != null && topClassName !in SDK_SCREENS
            }
            if (appTask != null) {
                Logger.verbose("App task found, bringing it to front as-is")
                appTask.moveToFront()
                return
            }
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        }
        Logger.verbose("No resumable app task, starting the launcher activity")
        IntentUtil.createLauncherIntent(this)?.let { startActivity(it) }
    }

    private companion object {
        /** SDK-owned full-screen activities; a task showing one of these on top is not host UI. */
        val SDK_SCREENS = setOf(
            NotificationClickedActivity::class.java.name,
            FlareLaneWebViewActivity::class.java.name
        )
    }
}

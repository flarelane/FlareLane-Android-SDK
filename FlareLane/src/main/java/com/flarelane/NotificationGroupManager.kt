package com.flarelane

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.math.absoluteValue

/**
 * Maintains one summary notification per threadId group, derived live from
 * [NotificationManager.getActiveNotifications] — no persistence layer. Reading the shade at
 * refresh time is inherently consistent with what the user actually sees (survives process
 * death, system eviction, and swipes), which is why no database of children is kept.
 *
 * Android requires a summary for custom groups: children with setGroup() but no
 * setGroupSummary(true) sibling can render blank/collapsed inconsistently (a real Android 14
 * bug class other push SDKs shipped fixes for).
 */
internal object NotificationGroupManager {
    // Summaries are addressed by (tag = threadId, fixed id): a hashCode-derived id could collide
    // between two different threadIds and let one group overwrite/cancel the other's summary.
    private const val SUMMARY_ID_PREFIX = "flarelane_summary_"

    /** Plain-id summaries (no tag): some OEM shades fail to visually merge groups whose
     *  summary is posted under a notification tag. */
    @JvmStatic
    fun summaryNotificationId(threadId: String): Int =
        (SUMMARY_ID_PREFIX + threadId).hashCode().absoluteValue
    private const val MAX_SUMMARY_LINES = 5

    /** A summary only makes sense once two or more children are visible. */
    @JvmStatic
    fun shouldShowSummary(childCount: Int): Boolean = childCount >= 2

    /**
     * InboxStyle lines from (title, text) pairs, newest first — pure so it is unit-testable
     * without Android framework types.
     */
    @JvmStatic
    fun buildSummaryLines(entries: List<Pair<CharSequence?, CharSequence?>>): List<CharSequence> =
        entries.take(MAX_SUMMARY_LINES).mapNotNull { (title, text) ->
            val parts = listOfNotNull(
                title?.takeIf { it.isNotEmpty() },
                text?.takeIf { it.isNotEmpty() }
            )
            if (parts.isEmpty()) null else parts.joinToString(" ")
        }

    /**
     * Re-derive and post/cancel the summary for [threadId]. Called after a child is posted,
     * clicked, or dismissed. Never throws — a summary failure must not affect the already
     * posted child notification.
     */
    @JvmStatic
    fun refreshSummary(context: Context, threadId: String, channelId: String) {
        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            val children = manager.activeNotifications.filter { sbn ->
                sbn.notification.group == threadId &&
                    (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0
            }.sortedByDescending { it.postTime }

            if (!shouldShowSummary(children.size)) {
                // Covers both "single child left" (child stands alone again) and "group emptied"
                // (no ghost summary lingering after the user cleared every child).
                manager.cancel(summaryNotificationId(threadId))
                return
            }

            val style = NotificationCompat.InboxStyle()
            buildSummaryLines(
                children.map { sbn ->
                    val extras = sbn.notification.extras
                    Pair(
                        extras.getCharSequence(android.app.Notification.EXTRA_TITLE),
                        extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
                    )
                }
            ).forEach { style.addLine(it) }

            val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()

            // Summary taps just open the app; they are not per-notification clicks, so no
            // CLICKED event should fire from here. requestCode collisions across groups are
            // harmless — every summary carries the identical launch intent.
            val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, threadId.hashCode().absoluteValue, it, PendingIntent.FLAG_IMMUTABLE)
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(resolveSmallIcon(context))
                .setContentTitle(appLabel)
                .setContentText(summaryText(children.size))
                .setStyle(style)
                .setGroup(threadId)
                .setGroupSummary(true)
                // Children alert; the summary itself must stay silent to avoid double sounds.
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
            contentIntent?.let { builder.setContentIntent(it) }

            manager.notify(summaryNotificationId(threadId), builder.build())
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        }
    }

    @JvmStatic
    fun summaryText(childCount: Int): String = "$childCount new notifications"

    /**
     * Same small-icon resolution the child notifications use (legacy setter → host app's
     * ic_stat_default drawable → system fallback), shared so summary and children match.
     */
    @JvmStatic
    fun resolveSmallIcon(context: Context): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (FlareLane.notificationIcon != 0) {
                    return FlareLane.notificationIcon
                }
                val defaultIconId = context.resources.getIdentifier(
                    Constants.ID_IC_STAT_DEFAULT, "drawable", context.packageName
                )
                if (defaultIconId != 0) {
                    return defaultIconId
                }
            }
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        }
        return android.R.drawable.ic_menu_info_details
    }
}

package com.flarelane

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired as the deleteIntent of grouped child notifications: when the user swipes one away,
 * the group summary must be re-derived so its count/lines stay truthful, and cancelled
 * entirely when the last child goes (no ghost summary).
 */
internal class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val threadId = intent.getStringExtra(EXTRA_THREAD_ID) ?: return
            val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: return
            NotificationGroupManager.refreshSummary(context, threadId, channelId)
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        }
    }

    companion object {
        private const val EXTRA_THREAD_ID = "flarelane_thread_id"
        private const val EXTRA_CHANNEL_ID = "flarelane_channel_id"

        /**
         * requestCode reuses the notification's own id: PendingIntents targeting this receiver
         * never collide with the click PendingIntents (different target component), and distinct
         * notifications get distinct delete intents.
         */
        @JvmStatic
        fun buildPendingIntent(
            context: Context,
            threadId: String,
            channelId: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, NotificationDismissedReceiver::class.java)
                .putExtra(EXTRA_THREAD_ID, threadId)
                .putExtra(EXTRA_CHANNEL_ID, channelId)
            // UPDATE_CURRENT: a replaced notification (stable flarelane_notification_id) reuses
            // this requestCode — its extras must reflect the latest threadId/channelId.
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}

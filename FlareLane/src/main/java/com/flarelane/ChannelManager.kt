package com.flarelane

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.flarelane.util.AndroidUtils

internal object ChannelManager {
    internal const val defaultChannelId = "com.flarelane.default_notification_channel_id"
    private const val DEFAULT_CHANNEL_NAME = "flarelane_default_channel_name"

    @JvmStatic
    fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                defaultChannelId, getChannelName(context), NotificationManager.IMPORTANCE_HIGH
            )
            channel.setShowBadge(true)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getChannelName(context: Context) = AndroidUtils.getResourceString(
        context.applicationContext, DEFAULT_CHANNEL_NAME,
        context.getString(R.string.default_notification_channel_name)
    )
}

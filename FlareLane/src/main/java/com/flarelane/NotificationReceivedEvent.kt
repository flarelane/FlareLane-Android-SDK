package com.flarelane

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.flarelane.notification.NotificationStyle
import com.flarelane.util.FileUtil
import com.flarelane.util.putParcelableDataClass
import java.util.Random

class NotificationReceivedEvent(
    private val context: Context,
    val notification: Notification
) {

    fun display() {
        try {
            val notifyId = System.currentTimeMillis().toInt()
            val flarelaneNotification = notification
            val projectId = BaseSharedPreferences.getProjectId(context, false)
            val deviceId = BaseSharedPreferences.getDeviceId(context, false)
            val isForeground = Helper.appInForeground(context)
            Thread {
                try {
                    val clickedIntent = Intent(context, NotificationClickedActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    clickedIntent.putParcelableDataClass(notification)
                    val contentIntent = PendingIntent.getActivity(
                        context,
                        Random().nextInt(543254),
                        clickedIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )

                    var builder = NotificationCompat.Builder(
                        context, ChannelManager.getDefaultChannelId()
                    )
                        .setSmallIcon(getNotificationIcon(context))
                        .setContentText(flarelaneNotification.body)
                        .setContentTitle(
                            flarelaneNotification.title
                                ?: context.applicationInfo.loadLabel(
                                    context.packageManager
                                ).toString()
                        )
                        .setAutoCancel(true)
                        .setContentIntent(contentIntent)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    try {
                        val accentColor = Helper.getResourceString(
                            context.applicationContext, Constants.ID_NOTIFICATION_ACCENT_COLOR
                        )
                        if (accentColor != null) {
                            builder = builder.setColor(Color.parseColor(accentColor))
                        }
                    } catch (e: Exception) {
                        BaseErrorHandler.handle(e)
                    }

                    FileUtil.downloadImageToBitmap(flarelaneNotification.imageUrl)
                        ?.let { image ->
                            builder
                                .setLargeIcon(image)
                                .setStyle(
                                    NotificationCompat.BigPictureStyle().bigPicture(image)
                                        .bigLargeIcon(null)
                                        .setSummaryText(flarelaneNotification.body)
                                )
                        } ?: run {
                        builder.setStyle(
                            NotificationCompat.BigTextStyle().bigText(flarelaneNotification.body)
                        )
                    }

                    val notification = builder.build()
                    notification.defaults =
                        notification.defaults or android.app.Notification.DEFAULT_SOUND
                    notification.defaults =
                        notification.defaults or android.app.Notification.DEFAULT_LIGHTS
                    notification.defaults =
                        notification.defaults or android.app.Notification.DEFAULT_VIBRATE

                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(notifyId, notification)

                    if (isForeground) {
                        EventService.createForegroundReceived(
                            projectId,
                            deviceId,
                            flarelaneNotification
                        )
                    } else {
                        EventService.createBackgroundReceived(
                            projectId,
                            deviceId,
                            flarelaneNotification
                        )
                    }
                } catch (e: Exception) {
                    BaseErrorHandler.handle(e)
                }
            }.start()
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun getNotificationIcon(context: Context): Int {
        try {
            // if default notification icon is exists
            val getDefaultIconId = context.resources.getIdentifier(
                Constants.ID_IC_STAT_DEFAULT,
                "drawable",
                context.packageName
            )
            if (getDefaultIconId != 0) {
                return getDefaultIconId
            }
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        }

        // Use a system default notification icon
        return android.R.drawable.ic_menu_info_details
    }

    private fun updateShortcuts(data: NotificationStyle?) {
        if (data !is NotificationStyle.NSMessaging) return

        data.id?.let {

        }
    }
}

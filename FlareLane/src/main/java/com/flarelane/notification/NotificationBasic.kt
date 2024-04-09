package com.flarelane.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import com.flarelane.Notification
import com.flarelane.util.FileUtil

internal class NotificationBasic(
    override val context: Context,
    override val notification: Notification,
    override val notificationStyle: NotificationStyle.Basic
) : NotificationCreator<NotificationStyle.Basic>() {
    override val requestContentId = 0

    override fun notify() {
        val largeIcon = FileUtil.downloadImageToBitmap(notification.largeIconUrl)
        val image = FileUtil.downloadImageToBitmap(notification.imageUrl)

        when {
            largeIcon != null && image != null -> {
                builder.setLargeIcon(largeIcon)
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(image)
                            .bigLargeIcon(null)
                            .setSummaryText(notification.body)
                    )
            }

            largeIcon != null -> {
                builder.setLargeIcon(largeIcon)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            }

            image != null -> {
                builder.setLargeIcon(image)
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(image)
                            .bigLargeIcon(null)
                            .setSummaryText(notification.body)
                    )
            }

            else -> {
                builder.setStyle(
                    NotificationCompat.BigTextStyle().bigText(notification.body)
                )
            }
        }

        super.notify()
    }
}

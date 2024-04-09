package com.flarelane.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import com.flarelane.BaseErrorHandler
import com.flarelane.BaseSharedPreferences
import com.flarelane.ChannelManager
import com.flarelane.Constants
import com.flarelane.EventService
import com.flarelane.Notification
import com.flarelane.NotificationClickedActivity
import com.flarelane.util.AndroidUtils
import com.flarelane.util.putParcelableDataClass

internal abstract class NotificationCreator<T : NotificationStyle> {
    protected abstract val context: Context
    protected abstract val notification: Notification
    protected abstract val notificationStyle: T
    protected abstract val requestContentId: Int
    protected open val autoCancel = true

    private val notifyId = System.currentTimeMillis().toInt()
    protected open val notifyTag = notifyId.toString()

    protected val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val contentIntent by lazy {
        PendingIntent.getActivity(
            context,
            requestContentId,
            Intent(context, NotificationClickedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putParcelableDataClass(notification)
            },
            flagUpdateCurrent(false)
        )
    }

    protected val builder by lazy {
        val builder = NotificationCompat.Builder(context, ChannelManager.defaultChannelId)
            .setSmallIcon(
                AndroidUtils.getResourceDrawableId(
                    context,
                    Constants.ID_IC_STAT_DEFAULT,
                    android.R.drawable.ic_menu_info_details
                )!!
            )
            .setContentTitle(
                notification.title ?: context.applicationInfo.loadLabel(context.packageManager)
            )
            .setContentText(notification.body)
            .setAutoCancel(autoCancel)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        AndroidUtils.getResourceString(context, Constants.ID_NOTIFICATION_ACCENT_COLOR)?.let {
            try {
                builder.setColor(Color.parseColor(it))
            } catch (e: Exception) {
                BaseErrorHandler.handle(e)
            }
        }
        builder
    }

    @WorkerThread
    internal open fun notify() {
        val n = builder.build().apply {
            defaults = defaults or android.app.Notification.DEFAULT_SOUND
            defaults = defaults or android.app.Notification.DEFAULT_LIGHTS
            defaults = defaults or android.app.Notification.DEFAULT_VIBRATE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.activeNotifications.find { it.tag == notifyTag }?.let {
                notificationManager.notify(it.tag, it.id, n)
            } ?: run {
                notificationManager.notify(notifyTag, notifyId, n)
            }
        } else {
            notificationManager.notify(notifyTag, notifyId, n)
        }

        sendEvent()
    }

    private fun sendEvent() {
        val projectId = BaseSharedPreferences.getProjectId(context, false)!!
        val deviceId = BaseSharedPreferences.getDeviceId(context, false)!!
        val isForeground = AndroidUtils.appInForeground(context)
        if (isForeground) {
            EventService.createForegroundReceived(
                projectId,
                deviceId,
                notification
            )
        } else {
            EventService.createBackgroundReceived(
                projectId,
                deviceId,
                notification
            )
        }
    }

    protected fun flagUpdateCurrent(mutable: Boolean): Int {
        return if (mutable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
    }

    companion object {
        internal fun create(context: Context, notification: Notification): NotificationCreator<*>? {
            return when (val notificationStyle = NotificationStyle.of(notification)) {
                is NotificationStyle.Basic -> {
                    NotificationBasic(context, notification, notificationStyle)
                }

                is NotificationStyle.Messaging -> {
                    NotificationMessaging(context, notification, notificationStyle)
                }

                null -> null
            }
        }
    }
}

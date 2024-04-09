package com.flarelane

import android.content.Context
import com.flarelane.notification.NotificationCreator

class NotificationReceivedEvent(
    private val context: Context,
    val notification: Notification
) {
    fun display() {
        Thread {
            try {
                NotificationCreator.create(context, notification)?.notify()
            } catch (e: Exception) {
                BaseErrorHandler.handle(e)
            }
        }.start()
    }
}

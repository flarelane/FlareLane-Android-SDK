package com.flarelane.notification

import com.flarelane.Notification

internal open class NotificationCreator(private val notification: Notification) {
    val notificationStyle = NotificationStyle.of(notification)

    internal open fun notify() {

    }
}

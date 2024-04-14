package com.flarelane.notification

import com.flarelane.Notification

data class NotificationClickEvent(
    @JvmField val notification: Notification,
    @JvmField val notificationClickedButton: NotificationClickedButton? = null
)

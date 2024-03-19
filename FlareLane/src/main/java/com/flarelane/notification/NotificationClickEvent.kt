package com.flarelane.notification

import com.flarelane.Notification

data class NotificationClickEvent(
    val notification: Notification,
    val notificationClickedButton: NotificationClickedButton? = null
)

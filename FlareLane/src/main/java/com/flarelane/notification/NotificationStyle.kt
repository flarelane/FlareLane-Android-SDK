package com.flarelane.notification

import com.flarelane.Notification
import com.flarelane.util.getStringOrNull

internal sealed interface NotificationStyle {
    val notification: Notification
    val type: NotificationStyleType

    data class NSBasic(
        override val notification: Notification,
        override val type: NotificationStyleType,
        val id: String?,
        val iconUrl: String
    ): NotificationStyle

    data class NSMessaging(
        override val notification: Notification,
        override val type: NotificationStyleType,
        val id: String?,
        val iconUrl: String
    ): NotificationStyle

    companion object {
        internal fun of(notification: Notification): NotificationStyle? {
            val dataJsonObject = notification.dataJsonObject ?: return null
            val typeString = dataJsonObject.getStringOrNull("type") ?: return null
            val type = NotificationStyleType.of(typeString) ?: return null

            return when (type) {
                NotificationStyleType.BASIC -> {
                    TODO()
                }
                NotificationStyleType.MESSAGING -> {
                    val iconUrl = dataJsonObject.getStringOrNull("iconUrl") ?: return null
                    NSMessaging(
                        notification = notification,
                        type = type,
                        id = dataJsonObject.getStringOrNull("id"),
                        iconUrl = iconUrl
                    )
                }
            }
        }
    }
}

internal enum class NotificationStyleType(val type: String) {
    // TODO name wording
    BASIC("basic"),
    MESSAGING("data_ms");

    companion object {
        fun of(type: String?) = entries.find { it.type == type } ?: BASIC
    }
}

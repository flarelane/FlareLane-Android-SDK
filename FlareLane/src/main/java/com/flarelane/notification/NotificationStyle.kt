package com.flarelane.notification

import com.flarelane.Notification
import com.flarelane.util.getStringOrNull

internal sealed interface NotificationStyle {
    val type: NotificationStyleType

    data class Basic(
        override val type: NotificationStyleType
    ) : NotificationStyle

    data class Messaging(
        override val type: NotificationStyleType,
        val tag: String,
        val iconUrl: String,
        val sender: String,
        val message: String
    ) : NotificationStyle

    companion object {
        internal fun of(notification: Notification): NotificationStyle? {
            val type = NotificationStyleType.of(
                notification.dataJsonObject.getStringOrNull("type")
            )

            return when (type) {
                NotificationStyleType.BASIC -> {
                    Basic(type = type)
                }

                NotificationStyleType.MESSAGING -> {
                    val sender =
                        notification.dataJsonObject.getStringOrNull("sender") ?: return null
                    Messaging(
                        type = type,
                        tag = notification.dataJsonObject.getStringOrNull("tag") ?: sender,
                        iconUrl = notification.dataJsonObject.getStringOrNull("iconUrl")
                            ?: return null,
                        sender = sender,
                        message = notification.dataJsonObject.getStringOrNull("message")
                            ?: return null
                    )
                }
            }
        }
    }
}

internal enum class NotificationStyleType(val type: String) {
    BASIC("basic"),
    MESSAGING("messaging");

    companion object {
        fun of(type: String?) = entries.find { it.type == type } ?: BASIC
    }
}

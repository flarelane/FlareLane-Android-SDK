package com.flarelane

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Chat-style (communication) notification payload: who the push should appear to be from.
 * Both fields are required — the whole point of the feature is the sender avatar, so a payload
 * missing either renders as a normal notification instead of a broken chat bubble.
 */
@Parcelize
data class NotificationCommunication(
    @JvmField val senderName: String,
    @JvmField val senderImageUrl: String
) : Parcelable

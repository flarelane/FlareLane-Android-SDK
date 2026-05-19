package com.flarelane

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NotificationButton(
    @JvmField val label: String,
    @JvmField val link: String?
) : Parcelable

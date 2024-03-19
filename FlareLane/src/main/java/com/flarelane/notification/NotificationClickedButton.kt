package com.flarelane.notification

import android.os.Parcelable
import com.flarelane.ReflectClass
import kotlinx.parcelize.Parcelize

@Parcelize
data class NotificationClickedButton(
    val id: String,
    val label: String,
    val link: String
) : Parcelable, ReflectClass

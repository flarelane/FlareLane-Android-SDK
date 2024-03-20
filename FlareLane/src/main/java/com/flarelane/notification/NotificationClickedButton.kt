package com.flarelane.notification

import android.os.Parcelable
import com.flarelane.ReflectClass
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class NotificationClickedButton(
    @JvmField val id: String,
    @JvmField val label: String,
    @JvmField val link: String
) : Parcelable, ReflectClass {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("buttonId"),
        jsonObject.getString("label"),
        jsonObject.getString("link")
    )
}

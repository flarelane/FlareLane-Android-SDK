package com.flarelane

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class Notification(
    @JvmField val id: String,
    @JvmField val body: String,
    @JvmField val data: String?,
    @JvmField val title: String?,
    @JvmField val url: String?,
    @JvmField val imageUrl: String?,
    @JvmField val buttons: String?
) : Parcelable, ReflectClass {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("notificationId"),
        jsonObject.getString("body"),
        jsonObject.getString("data"),
        if (jsonObject.has("title")) jsonObject.getString("title") else null,
        if (jsonObject.has("url")) jsonObject.getString("url") else null,
        if (jsonObject.has("imageUrl")) jsonObject.getString("imageUrl") else null,
        if (jsonObject.has("buttons")) jsonObject.getString("buttons") else null
    )

    @IgnoredOnParcel
    val dataJsonObject by lazy {
        try {
            if (data != null) {
                JSONObject(data)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    @IgnoredOnParcel
    val buttonsJsonArray by lazy {
        try {
            if (buttons != null) {
                JSONArray(buttons)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

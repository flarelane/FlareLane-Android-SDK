package com.flarelane

import android.os.Bundle
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
) : Parcelable, InteractionClass {
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

    override fun toHashMap(): HashMap<String, Any?> {
        return hashMapOf<String, Any?>().also {
            it["id"] = id
            it["title"] = title
            it["body"] = body
            it["url"] = url
            it["imageUrl"] = imageUrl
            it["data"] = data
        }
    }

    override fun toBundle(): Bundle {
        return Bundle().also {
            it.putString("id", id)
            it.putString("title", title)
            it.putString("body", body)
            it.putString("url", url)
            it.putString("imageUrl", imageUrl)
            it.putString("data", data)
        }
    }
}

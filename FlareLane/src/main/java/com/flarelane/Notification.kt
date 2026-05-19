package com.flarelane

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import com.flarelane.util.AndroidUtils
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import kotlin.math.absoluteValue

@Parcelize
data class Notification(
    @JvmField val id: String,
    @JvmField val body: String,
    @JvmField val data: String?,
    @JvmField val title: String?,
    @JvmField val url: String?,
    @JvmField val imageUrl: String?,
    @JvmField val buttons: String?,
    @JvmField val clickedButtonIdx: Int? = null
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

    val clickedButton: NotificationButton?
        get() = clickedButtonIdx?.let { buttonList.getOrNull(it) }

    val clickedUrl: String?
        get() = if (clickedButtonIdx != null) clickedButton?.link else url

    fun withClickedButtonIdx(idx: Int): Notification = copy(clickedButtonIdx = idx)

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
    val buttonList: List<NotificationButton> by lazy {
        try {
            if (buttons.isNullOrEmpty()) {
                emptyList()
            } else {
                val array = JSONArray(buttons)
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    val label = obj.optString("label")
                    if (label.isNullOrEmpty()) return@mapNotNull null
                    NotificationButton(
                        label = label,
                        link = if (obj.has("link")) obj.optString("link") else null
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun toHashMap(): HashMap<String, Any?> {
        val button = clickedButton
        return hashMapOf<String, Any?>().also {
            it["id"] = id
            it["title"] = title
            it["body"] = body
            it["url"] = url
            it["imageUrl"] = imageUrl
            it["data"] = data
            it["buttons"] = buttons
            it["clickedButtonIdx"] = clickedButtonIdx
            it["clickedButtonLabel"] = button?.label
            it["clickedButtonLink"] = button?.link
        }
    }

    override fun toBundle(): Bundle {
        val idx = clickedButtonIdx
        val button = clickedButton
        return Bundle().also {
            it.putString("id", id)
            it.putString("title", title)
            it.putString("body", body)
            it.putString("url", url)
            it.putString("imageUrl", imageUrl)
            it.putString("data", data)
            it.putString("buttons", buttons)
            if (idx != null) {
                it.putInt("clickedButtonIdx", idx)
            }
            it.putString("clickedButtonLabel", button?.label)
            it.putString("clickedButtonLink", button?.link)
        }
    }

    fun currentAndroidNotificationId(): Int {
        val notificationId = dataJsonObject?.optString(Constants.NOTIFICATION_ID)

        return if (notificationId != null && !notificationId.contentEquals("")) {
            notificationId.hashCode().absoluteValue
        } else {
            id.hashCode().absoluteValue
        }
    }

    fun currentChannelId(context: Context): String {
        val channelId = dataJsonObject?.optString(Constants.NOTIFICATION_CHANNEL_ID)

        return if (channelId != null && !channelId.contentEquals("")) {
            channelId
        } else {
            ChannelManager.getDefaultChannelId(context)
        }
    }
}

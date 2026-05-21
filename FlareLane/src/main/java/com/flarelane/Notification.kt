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
    // `buttons` defaults to null so existing Java/Kotlin call sites that predate 1.10.0
    // (and only pass id/body/data/title/url/imageUrl positionally) keep compiling against
    // the data-class primary constructor.
    @JvmField val buttons: String? = null,
    @JvmField val clickedButtonIndex: Int? = null
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

    /** Parsed list of action buttons; empty when the payload doesn't include any. Malformed
     *  entries are skipped individually rather than failing the whole list. */
    @IgnoredOnParcel
    val buttonList: List<NotificationButton> by lazy {
        if (buttons.isNullOrEmpty()) return@lazy emptyList()
        try {
            val array = JSONArray(buttons)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val label = obj.optString("label").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val link = obj.optString("link").takeIf { it.isNotEmpty() }
                NotificationButton(label, link)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** The button the user actually tapped, or null for a body click / out-of-range index.
     *  Prefer this object accessor over reaching into `buttons[clickedButtonIndex]` yourself. */
    val clickedButton: NotificationButton?
        get() = clickedButtonIndex?.let { buttonList.getOrNull(it) }

    /** URL associated with the click — picks one of two sources based on **what was clicked**:
     *
     *   - **Button click** (`clickedButtonIndex != null`): the tapped button's link, or `null`
     *     when the button has no link (including out-of-range / missing button data).
     *   - **Body click** (`clickedButtonIndex == null`): the notification body's [url], or
     *     `null` when none is set.
     *
     *  ⚠️ A button click with no link returns `null`, **not** the body's [url]. Body and
     *  button URLs are conceptually distinct destinations — the click target determines which
     *  source is valid, and falling through would silently navigate users to the body URL
     *  when they tapped a button that intentionally has none. */
    val clickedUrl: String?
        get() = if (clickedButtonIndex != null) clickedButton?.link else url

    /** Returns a copy carrying the index of the button that was tapped. */
    fun withClickedButtonIndex(idx: Int): Notification = copy(clickedButtonIndex = idx)

    override fun toHashMap(): HashMap<String, Any?> {
        // Pre-compute every derived value here so cross-platform consumers (RN/Flutter) can stay
        // read-only — they only declare fields, never reproduce branching logic. Keeps the
        // notion of "what was clicked / where to go" pinned to the native source of truth.
        val clicked = clickedButton
        return hashMapOf<String, Any?>().also {
            it["id"] = id
            it["title"] = title
            it["body"] = body
            it["url"] = url
            it["imageUrl"] = imageUrl
            // Send `data` as a parsed Map (not the raw JSON string) so RN/Flutter receive an
            // object directly — matches iOS, which already passes a Dictionary. Native Kotlin
            // callers still see the raw string via the `data` property; this conversion is
            // bridge-only.
            it["data"] = dataJsonObject?.toMapOrNull()
            it["buttons"] = buttonList.map { btn ->
                hashMapOf<String, Any?>("label" to btn.label, "link" to btn.link)
            }
            it["clickedButtonIndex"] = clickedButtonIndex
            it["clickedButton"] = clicked?.let { btn ->
                hashMapOf<String, Any?>("label" to btn.label, "link" to btn.link)
            }
            it["clickedUrl"] = clickedUrl
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
            it.putString("buttons", buttons)
            clickedButtonIndex?.let { idx -> it.putInt("clickedButtonIndex", idx) }
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

// Recursive JSONObject/JSONArray → plain Kotlin Map/List conversion for the cross-platform
// bridge. Lives in this file because `data` is the only field that needs it today; promote to
// a shared util if another caller appears.
private fun JSONObject.toMapOrNull(): Map<String, Any?>? = try {
    val map = mutableMapOf<String, Any?>()
    val keyIter = keys()
    while (keyIter.hasNext()) {
        val key = keyIter.next()
        map[key] = unwrapJsonValue(opt(key))
    }
    map
} catch (_: Exception) {
    null
}

private fun JSONArray.toAnyList(): List<Any?> =
    (0 until length()).map { unwrapJsonValue(opt(it)) }

private fun unwrapJsonValue(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> value.toMapOrNull()
    is JSONArray -> value.toAnyList()
    else -> value
}

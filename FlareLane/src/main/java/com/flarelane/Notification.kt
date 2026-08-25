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
data class Notification @JvmOverloads constructor(
    @JvmField val id: String,
    @JvmField val body: String,
    @JvmField val data: String?,
    @JvmField val title: String?,
    @JvmField val url: String?,
    @JvmField val imageUrl: String?,
    // `buttons` defaults to null so existing Java/Kotlin call sites that predate 1.10.0
    // (and only pass id/body/data/title/url/imageUrl positionally) keep compiling against
    // the data-class primary constructor. `@JvmOverloads` makes the 6/7/8-arg variants
    // visible to Java so a `new Notification(id, body, data, title, url, imageUrl)` call
    // from an older host app still resolves.
    @JvmField val buttons: String? = null,
    // Notification-grouping key (iOS thread-id counterpart); raw string from the payload.
    @JvmField val threadId: String? = null,
    // Chat-style sender payload; raw JSON string like `buttons`, parsed lazily below.
    @JvmField val communication: String? = null,
    @JvmField val clickedButtonIndex: Int? = null
) : Parcelable, InteractionClass {
    // Restores the pre-1.11.0 8-arg JVM signature (..., clickedButtonIndex last) that the new
    // threadId/communication parameters displaced, so already-compiled callers keep linking.
    constructor(
        id: String,
        body: String,
        data: String?,
        title: String?,
        url: String?,
        imageUrl: String?,
        buttons: String?,
        clickedButtonIndex: Int?
    ) : this(id, body, data, title, url, imageUrl, buttons, null, null, clickedButtonIndex)

    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("notificationId"),
        jsonObject.getString("body"),
        jsonObject.getString("data"),
        if (jsonObject.has("title")) jsonObject.getString("title") else null,
        if (jsonObject.has("url")) jsonObject.getString("url") else null,
        if (jsonObject.has("imageUrl")) jsonObject.getString("imageUrl") else null,
        if (jsonObject.has("buttons")) jsonObject.getString("buttons") else null,
        // isNull 가드: 명시적 JSON null 이 오면 getString 이 문자열 "null" 을 돌려줘
        // 엉뚱한 그룹키("null")가 생길 수 있다.
        if (jsonObject.has("threadId") && !jsonObject.isNull("threadId")) {
            jsonObject.getString("threadId").takeIf { it.isNotEmpty() }
        } else null,
        if (jsonObject.has("communication") && !jsonObject.isNull("communication")) {
            jsonObject.getString("communication")
        } else null
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

    /** Parsed chat-style sender, or null when the payload has none or misses a required field
     *  (both senderName and senderImageUrl are required — see [NotificationCommunication]).
     *  Malformed JSON never throws; it falls back to a normal notification. */
    @IgnoredOnParcel
    val communicationData: NotificationCommunication? by lazy {
        if (communication.isNullOrEmpty()) return@lazy null
        try {
            val obj = JSONObject(communication)
            val senderName = obj.optString("senderName").takeIf { it.isNotEmpty() } ?: return@lazy null
            val senderImageUrl = obj.optString("senderImageUrl").takeIf { it.isNotEmpty() } ?: return@lazy null
            NotificationCommunication(senderName, senderImageUrl)
        } catch (_: Exception) {
            null
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
            it["threadId"] = threadId
            it["communication"] = communicationData?.let { comm ->
                hashMapOf<String, Any?>(
                    "senderName" to comm.senderName,
                    "senderImageUrl" to comm.senderImageUrl
                )
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
            it.putString("threadId", threadId)
            it.putString("communication", communication)
            clickedButtonIndex?.let { idx -> it.putInt("clickedButtonIndex", idx) }
        }
    }

    /** Stable android notification id for chat-style pushes: every push in the same
     *  conversation (threadId, falling back to this notification's id) reuses one id so
     *  messages stack messenger-style instead of piling up. */
    fun conversationNotificationId(): Int {
        val key = threadId?.takeIf { it.isNotEmpty() } ?: id
        return ("flarelane_conversation_$key").hashCode().absoluteValue
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

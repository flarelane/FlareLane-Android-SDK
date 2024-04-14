package com.flarelane.notification

import android.os.Bundle
import android.os.Parcelable
import com.flarelane.InteractionClass
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class NotificationClickedButton(
    @JvmField val id: String,
    @JvmField val label: String,
    @JvmField val link: String
) : Parcelable, InteractionClass {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("buttonId"),
        jsonObject.getString("label"),
        jsonObject.getString("link")
    )

    override fun toHashMap(): HashMap<String, Any?> {
        return hashMapOf<String, Any?>().also {
            it["buttonId"] = id
            it["label"] = label
            it["link"] = link
        }
    }

    override fun toBundle(): Bundle {
        return Bundle().also {
            it.putString("buttonId", id)
            it.putString("label", label)
            it.putString("link", link)
        }
    }
}

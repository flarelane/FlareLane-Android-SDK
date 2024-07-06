package com.flarelane

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class InAppMessage(
    @JvmField val id: String,
) : Parcelable, InteractionClass {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("id"),
    )

    override fun toHashMap(): HashMap<String, Any?> {
        return hashMapOf<String, Any?>().also {
            it["id"] = id
        }
    }

    override fun toBundle(): Bundle {
        return Bundle().also {
            it.putString("id", id)
        }
    }
}

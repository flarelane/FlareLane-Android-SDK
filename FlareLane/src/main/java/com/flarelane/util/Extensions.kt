package com.flarelane.util

import com.flarelane.Logger
import org.json.JSONException
import org.json.JSONObject

fun String.toJSONObject(run: (JSONObject) -> Unit) {
    var jsonObject: JSONObject? = null
    try {
        jsonObject = JSONObject(this)
    } catch (e: JSONException) {
        Logger.error("toJSONObject() error, e=$e")
    }
    jsonObject?.let {
        run.invoke(it)
    }
}

fun String?.toJSONObjectWithNull(run: (JSONObject?) -> Unit) {
    val jsonObject: JSONObject?
    try {
        jsonObject = this?.let { JSONObject(it) }
    } catch (e: JSONException) {
        Logger.error("toJSONObjectOrNull() error, e=$e")
        return
    }
    run.invoke(jsonObject)
}

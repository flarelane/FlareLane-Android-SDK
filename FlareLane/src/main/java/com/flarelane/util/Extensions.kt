package com.flarelane.util

import com.flarelane.Logger
import org.json.JSONException
import org.json.JSONObject

fun String?.toJSONObjectOrNull() =  this?.let {
    try {
        JSONObject(it)
    } catch (e: JSONException) {
        Logger.error("toJSONObjectOrNull() error, e=$e")
        null
    }
}

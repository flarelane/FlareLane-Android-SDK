package com.flarelane

import android.util.Log

object Logger {
    /**
     * Android Log priority. Lower priority = more verbose, so we keep `logLevel` as
     * the minimum priority to emit. Default `Log.VERBOSE` = emit everything.
     */
    @JvmField
    var logLevel = Log.VERBOSE

    @JvmStatic
    @JvmOverloads
    fun error(module: String, message: String, kv: Map<String, Any?>? = null) {
        if (logLevel > Log.ERROR) return
        Log.e("FlareLane", format("ERROR", module, message, kv))
    }

    @JvmStatic
    @JvmOverloads
    fun info(module: String, message: String, kv: Map<String, Any?>? = null) {
        if (logLevel > Log.INFO) return
        Log.i("FlareLane", format("INFO", module, message, kv))
    }

    @JvmStatic
    @JvmOverloads
    fun verbose(module: String, message: String, kv: Map<String, Any?>? = null) {
        if (logLevel > Log.VERBOSE) return
        Log.v("FlareLane", format("VERBOSE", module, message, kv))
    }

    private fun format(levelTag: String, module: String, message: String, kv: Map<String, Any?>?): String {
        val base = "[FlareLane][$levelTag][$module] $message"
        if (kv.isNullOrEmpty()) return base
        val pairs = kv.entries
            .sortedBy { it.key }
            .joinToString(" ") { "${it.key}=${stringify(it.value)}" }
        return "$base  $pairs"
    }

    private fun stringify(v: Any?): String = when (v) {
        null -> "null"
        is String -> v
        is org.json.JSONObject, is org.json.JSONArray -> v.toString()
        else -> v.toString()
    }
}

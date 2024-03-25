package com.flarelane

import android.util.Log

internal object Logger {
    @JvmField
    var logLevel = Log.VERBOSE

    @JvmStatic
    fun verbose(str: String?) {
        if (logLevel <= Log.VERBOSE) Log.v("FlareLane", str!!)
    }

    @JvmStatic
    fun error(str: String?) {
        if (logLevel <= Log.ERROR) Log.e("FlareLane", str!!)
    }
}

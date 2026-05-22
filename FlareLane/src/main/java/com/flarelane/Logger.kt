package com.flarelane

import android.util.Log

/**
 * Single SDK-wide logger. The output prefix is unified across all four FlareLane SDKs
 * to `[FlareLane][LEVEL] message` so the same log line is recognizable on any platform
 * and easily parsed by tooling (including LLM-assisted log inspection).
 *
 * The Android-specific tag column already prints `FlareLane:` in logcat, so this file
 * only prepends `[LEVEL]` to the message — the rendered line in logcat ends up as
 * `FlareLane: [LEVEL] message`, which composes naturally with the other SDKs.
 */
internal object Logger {
    @JvmField
    var logLevel = Log.VERBOSE

    @JvmStatic
    fun verbose(str: String?) {
        if (logLevel <= Log.VERBOSE) Log.v("FlareLane", "[VERBOSE] ${str ?: ""}")
    }

    /**
     * Pass [throwable] when reporting a caught exception. `Log.e` then renders the full
     * stack trace automatically — caller no longer needs `Log.getStackTraceString(e)`.
     */
    @JvmStatic
    @JvmOverloads
    fun error(str: String?, throwable: Throwable? = null) {
        if (logLevel <= Log.ERROR) Log.e("FlareLane", "[ERROR] ${str ?: ""}", throwable)
    }
}

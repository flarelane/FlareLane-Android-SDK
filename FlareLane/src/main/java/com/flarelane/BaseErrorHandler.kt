package com.flarelane

import android.util.Log
import com.flarelane.Logger.error

internal object BaseErrorHandler {

    @JvmStatic
    fun handle(e: Exception?) {
        error(Log.getStackTraceString(e))
    }
}

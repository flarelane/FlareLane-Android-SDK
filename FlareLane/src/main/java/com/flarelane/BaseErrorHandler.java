package com.flarelane;

import android.util.Log;

class BaseErrorHandler {
    static void handle(Exception e) {
        com.flarelane.Logger.error(Log.getStackTraceString(e));
    }
}

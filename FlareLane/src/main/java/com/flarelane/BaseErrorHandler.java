package com.flarelane;

import android.util.Log;

import java.util.Collections;

class BaseErrorHandler {
    static void handle(Exception e) {
        com.flarelane.Logger.error("Error", "uncaught exception", Collections.singletonMap("stackTrace", Log.getStackTraceString(e)));
    }
}

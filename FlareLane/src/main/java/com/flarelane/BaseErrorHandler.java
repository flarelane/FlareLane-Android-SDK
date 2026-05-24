package com.flarelane;

class BaseErrorHandler {
    static void handle(Exception e) {
        // Logger.error(msg, throwable) lets logcat render the stack trace automatically —
        // no need to format it ourselves via Log.getStackTraceString.
        com.flarelane.Logger.error("Caught exception", e);
    }
}

package com.flarelane;

import android.util.Log;

public class Logger {
    static int logLevel = Log.VERBOSE;

    public static void verbose(String str) {
        if (logLevel <= Log.VERBOSE)
            Log.v("FlareLane", str);
    }

    public static void error(String str) {
        if (logLevel <= Log.ERROR)
            Log.e("FlareLane", str);
    }
}

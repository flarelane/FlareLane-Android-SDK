package com.flarelane;

import android.util.Log;

class Logger {
    static int logLevel = Log.VERBOSE;

    static void verbose(String str) {
        if (logLevel <= Log.VERBOSE)
            Log.v("FlareLane", str);
    }

    static void error(String str) {
        if (logLevel <= Log.ERROR)
            Log.e("FlareLane", str);
    }
}

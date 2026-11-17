package com.obddroid.utils;

import android.util.Log;

import com.obddroid.BuildConfig;

/**
 * Debug logging that is silent in release builds.
 *
 * Release builds are not minified, so R8 cannot strip android.util.Log.d calls. App-layer code
 * logs debug detail through this class instead; warnings and errors still go to Log.w / Log.e.
 */
public final class DebugLog {

    /** True in debug builds. Callers can test it before building an expensive message. */
    public static final boolean ENABLED = BuildConfig.DEBUG;

    private DebugLog() {
    }

    public static void d(String tag, String msg) {
        if (ENABLED) {
            Log.d(tag, msg);
        }
    }

    public static void d(String tag, String msg, Throwable tr) {
        if (ENABLED) {
            Log.d(tag, msg, tr);
        }
    }
}

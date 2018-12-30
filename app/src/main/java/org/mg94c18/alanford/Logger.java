package org.mg94c18.alanford;

import android.util.Log;

public class Logger {
    public static final String TAG = BuildConfig.FLAVOR;

    public static void LOG_V(String s) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, s);
        }
    }
}

package com.aniket.tapshare.util

import android.util.Log

/** All app logs use one tag so `adb logcat -s TapShare` shows everything. */
object TapLog {
    private const val TAG = "TapShare"

    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String, error: Throwable? = null) = Log.w(TAG, message, error)
    fun e(message: String, error: Throwable? = null) = Log.e(TAG, message, error)
}

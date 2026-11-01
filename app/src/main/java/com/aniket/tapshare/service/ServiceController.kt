package com.aniket.tapshare.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.util.TapLog

/**
 * Starts/stops [TapShareService]. Call [sync] only while our Activity is visible: Android 12+
 * forbids starting a foreground service from the background, but allows it from a visible app.
 */
object ServiceController {

    fun sync(context: Context, shouldRun: Boolean) {
        val running = context.appContainer.serviceRunning.value
        when {
            shouldRun && !running -> start(context)
            !shouldRun && running -> stop(context)
        }
    }

    /** Start regardless of the "running" flag (boot receiver, or when we know it's not running). */
    fun startNow(context: Context) = start(context)

    private fun start(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, TapShareService::class.java))
            TapLog.i("Service: start requested")
        } catch (e: IllegalStateException) {
            // Defensive: thrown if we were somehow not in the foreground.
            TapLog.e("Service: could not start", e)
        }
    }

    private fun stop(context: Context) {
        context.stopService(Intent(context, TapShareService::class.java))
        TapLog.i("Service: stop requested")
    }
}

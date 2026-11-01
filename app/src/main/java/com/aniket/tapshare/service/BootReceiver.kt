package com.aniket.tapshare.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.util.TapLog

/**
 * After a reboot or an app update, start the ready service again — but only if the user wants
 * TapShare to work when closed and everything it needs is granted. Starting a connectedDevice
 * foreground service from BOOT_COMPLETED / MY_PACKAGE_REPLACED is allowed (exempt from the
 * background-start rules; not one of Android 15's boot-restricted service types).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val c = context.appContainer
        val ready = c.settings.sharingEnabled.value &&
            c.settings.backgroundEnabled.value &&
            c.permissions.summary.value.allRequiredGranted
        TapLog.i("Boot: ${intent.action} → start service = $ready")
        if (ready) ServiceController.startNow(context)
    }
}

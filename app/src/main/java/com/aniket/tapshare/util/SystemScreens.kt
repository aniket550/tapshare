package com.aniket.tapshare.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Opens system settings pages. Some OEM builds lack a page, so each one falls back safely. */
object SystemScreens {

    fun appDetails(context: Context) {
        open(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            fallback = Intent(Settings.ACTION_SETTINGS),
        )
    }

    fun appNotifications(context: Context) {
        open(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    fun bluetooth(context: Context) {
        open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), fallback = Intent(Settings.ACTION_SETTINGS))
    }

    fun location(context: Context) {
        open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), fallback = Intent(Settings.ACTION_SETTINGS))
    }

    private fun open(context: Context, intent: Intent, fallback: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            TapLog.w("No screen for ${intent.action}, using fallback", e)
            try {
                context.startActivity(fallback)
            } catch (e2: ActivityNotFoundException) {
                TapLog.e("No fallback screen for ${fallback.action}", e2)
            }
        }
    }
}

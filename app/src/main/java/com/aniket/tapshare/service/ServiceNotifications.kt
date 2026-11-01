package com.aniket.tapshare.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aniket.tapshare.R
import com.aniket.tapshare.ui.MainActivity

/** Builds the foreground-service notification. Quiet (low importance): no sound, no pop-up. */
class ServiceNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .setShowBadge(false)
            .build()
        manager.createNotificationChannel(channel)
    }

    fun build(readiness: Readiness, bluetooth: BluetoothStatus): Notification {
        val title = when (readiness) {
            Readiness.Ready -> context.getString(R.string.notification_ready_title)
            is Readiness.Paused -> context.getString(R.string.notification_paused_title)
            is Readiness.Blocked -> context.getString(readiness.text(bluetooth).title)
        }
        val body = context.getString(readiness.text(bluetooth).body)

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val turnOff = PendingIntent.getService(
            context,
            REQUEST_TURN_OFF,
            Intent(context, TapShareService::class.java).setAction(TapShareService.ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tapshare)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.notification_action_turn_off), turnOff)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Updates the visible notification. Skipped (not crashed) if notifications aren't allowed. */
    fun update(notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed && manager.areNotificationsEnabled()) {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "ready"
        const val NOTIFICATION_ID = 1
        private const val REQUEST_OPEN = 10
        private const val REQUEST_TURN_OFF = 11
    }
}

package com.aniket.tapshare.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.util.TapLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service (type connectedDevice) that keeps TapShare alive while sharing is on.
 *
 * It watches [Readiness]: BLE discovery runs only in [Readiness.Ready]
 * (sharing on + screen on + unlocked + Bluetooth on) and stops in every other state.
 */
class TapShareService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null
    private lateinit var notifications: ServiceNotifications

    override fun onCreate() {
        super.onCreate()
        notifications = ServiceNotifications(this).also { it.ensureChannel() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = appContainer

        if (intent?.action == ACTION_TURN_OFF) {
            TapLog.i("Service: turned off from notification")
            container.settings.setSharingEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }

        // Every startForegroundService() must be answered by startForeground() within a few seconds.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        container.setServiceRunning(true)

        if (watchJob == null) {
            watchJob = scope.launch {
                container.conditions.conditions
                    .map { ReadinessPolicy.evaluate(it) to it.bluetooth }
                    .distinctUntilChanged()
                    .collect { (readiness, bluetooth) -> onReadinessChanged(readiness, bluetooth) }
            }
        }
        // Sticky when the user wants TapShare to work while closed: if Android kills us, it restarts
        // the service; if that restart isn't allowed to go foreground, enterForeground() fails safely.
        return if (container.settings.backgroundEnabled.value) START_STICKY else START_NOT_STICKY
    }

    private fun enterForeground(): Boolean {
        val container = appContainer
        val conditions = container.conditions.snapshot()
        val notification = notifications.build(ReadinessPolicy.evaluate(conditions), conditions.bluetooth)
        return try {
            ServiceCompat.startForeground(
                this,
                ServiceNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            true
        } catch (e: SecurityException) {
            // A connectedDevice prerequisite is missing (should not happen: CHANGE_WIFI_STATE is declared).
            TapLog.e("Service: startForeground refused", e)
            false
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+): started while app in background.
            TapLog.e("Service: foreground start not allowed", e)
            false
        }
    }

    private fun onReadinessChanged(readiness: Readiness, bluetooth: BluetoothStatus) {
        val radiosOn = readiness == Readiness.Ready
        TapLog.i("Service: readiness=$readiness bluetooth=$bluetooth radios=${if (radiosOn) "ON" else "OFF"}")
        val discovery = appContainer.discovery
        if (radiosOn) discovery.start() else discovery.stop()
        notifications.update(notifications.build(readiness, bluetooth))
    }

    override fun onDestroy() {
        scope.cancel()
        appContainer.discovery.stop()
        appContainer.setServiceRunning(false)
        TapLog.i("Service: stopped")
        super.onDestroy()
    }

    companion object {
        const val ACTION_TURN_OFF = "com.aniket.tapshare.action.TURN_OFF"
    }
}

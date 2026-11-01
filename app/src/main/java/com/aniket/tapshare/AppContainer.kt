package com.aniket.tapshare

import android.app.Application
import com.aniket.tapshare.data.MyCardStore
import com.aniket.tapshare.data.SettingsStore
import com.aniket.tapshare.discovery.DiscoveryEngine
import com.aniket.tapshare.session.SessionManager
import com.aniket.tapshare.share.Outbox
import com.aniket.tapshare.ui.animation.GlowOverlay
import com.aniket.tapshare.ui.animation.Haptics
import com.aniket.tapshare.service.DeviceConditionsSource
import com.aniket.tapshare.util.PermissionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the single instance of each long-lived object (no DI framework needed at this size). */
class AppContainer(app: Application) {

    /** Lives as long as the process. Flows shared here stop when nobody collects them. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(app)

    val permissions = PermissionsRepository(app, settings)

    val conditions = DeviceConditionsSource(app, settings, permissions, appScope)

    /** BLE advertise + scan. Only TapShareService starts/stops it. */
    val discovery = DiscoveryEngine(app, settings, appScope)

    val myCard = MyCardStore(app)

    /** Photos/files waiting to be sent (share sheet or pickers). */
    val outbox = Outbox(app)

    val haptics = Haptics(app)

    /** One tap = one session (contact exchange or photo/file transfer). */
    /** Rainbow glow over other apps when a tap happens with TapShare closed. */
    val glowOverlay = GlowOverlay(app)

    val session = SessionManager(app, appScope, discovery, myCard, outbox, haptics, settings, glowOverlay)

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    internal fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }
}

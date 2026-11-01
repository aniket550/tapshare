package com.aniket.tapshare.service

import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.aniket.tapshare.data.SettingsStore
import com.aniket.tapshare.util.PermissionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Turns system state (screen, lock, Bluetooth, Location, our settings, permissions) into one
 * [StateFlow] of [DeviceConditions], plus the derived [Readiness].
 *
 * Receivers are registered only while someone collects (the service or a visible screen),
 * so nothing listens when TapShare is fully idle. No polling.
 */
class DeviceConditionsSource(
    private val context: Context,
    private val settings: SettingsStore,
    private val permissions: PermissionsRepository,
    scope: CoroutineScope,
) {
    private val powerManager = requireNotNull(context.getSystemService(PowerManager::class.java))
    private val keyguardManager = requireNotNull(context.getSystemService(KeyguardManager::class.java))
    private val locationManager = requireNotNull(context.getSystemService(LocationManager::class.java))
    private val bluetoothAdapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** BLE scans return nothing while Location is off, but only up to Android 11. */
    private val locationServicesNeeded = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R

    private val screen: Flow<ScreenState> = broadcastFlow(
        listOf(Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT),
        ::readScreen,
    )

    private val bluetooth: Flow<BluetoothStatus> =
        if (bluetoothAdapter == null) {
            flowOf(BluetoothStatus.UNSUPPORTED)
        } else {
            broadcastFlow(listOf(BluetoothAdapter.ACTION_STATE_CHANGED), ::readBluetooth)
        }

    private val locationOn: Flow<Boolean> =
        if (!locationServicesNeeded) {
            flowOf(true)
        } else {
            broadcastFlow(
                listOf(LocationManager.MODE_CHANGED_ACTION, LocationManager.PROVIDERS_CHANGED_ACTION),
                ::readLocationOn,
            )
        }

    val conditions: StateFlow<DeviceConditions> = combine(
        settings.sharingEnabled,
        permissions.summary.map { it.allRequiredGranted }.distinctUntilChanged(),
        bluetooth,
        locationOn,
        screen,
    ) { sharing, granted, bt, location, screenState ->
        DeviceConditions(
            sharingEnabled = sharing,
            permissionsGranted = granted,
            bluetooth = bt,
            locationServicesNeeded = locationServicesNeeded,
            locationServicesOn = location,
            screen = screenState,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = snapshot())

    val readiness: StateFlow<Readiness> = conditions
        .map(ReadinessPolicy::evaluate)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReadinessPolicy.evaluate(snapshot()))

    /** Reads everything right now (used as the first value before receivers deliver). */
    fun snapshot() = DeviceConditions(
        sharingEnabled = settings.sharingEnabled.value,
        permissionsGranted = permissions.summary.value.allRequiredGranted,
        bluetooth = if (bluetoothAdapter == null) BluetoothStatus.UNSUPPORTED else readBluetooth(),
        locationServicesNeeded = locationServicesNeeded,
        locationServicesOn = !locationServicesNeeded || readLocationOn(),
        screen = readScreen(),
    )

    private fun readScreen() = ScreenState(
        interactive = powerManager.isInteractive,
        locked = keyguardManager.isKeyguardLocked,
    )

    private fun readBluetooth(): BluetoothStatus {
        val adapter = bluetoothAdapter ?: return BluetoothStatus.UNSUPPORTED
        return try {
            when (adapter.state) {
                BluetoothAdapter.STATE_ON -> BluetoothStatus.ON
                BluetoothAdapter.STATE_TURNING_ON -> BluetoothStatus.TURNING_ON
                BluetoothAdapter.STATE_TURNING_OFF -> BluetoothStatus.TURNING_OFF
                else -> BluetoothStatus.OFF
            }
        } catch (e: SecurityException) {
            // Some OEM builds guard getState(); treat as off rather than crash.
            BluetoothStatus.OFF
        }
    }

    private fun readLocationOn(): Boolean = LocationManagerCompat.isLocationEnabled(locationManager)

    /**
     * The broadcast is only a "something changed" signal: we always re-read the real value from
     * the system manager, so a spoofed or out-of-order intent can't put us in a wrong state.
     *
     * RECEIVER_EXPORTED is required: Bluetooth state comes from the Bluetooth app (privileged but
     * not the system UID), and NOT_EXPORTED receivers don't get those broadcasts.
     */
    private fun <T> broadcastFlow(actions: List<String>, read: () -> T): Flow<T> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        trySend(read()) // after registering, so no change between read and register is missed
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private companion object {
        /** Keeps receivers alive across quick config changes (rotation) instead of re-registering. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

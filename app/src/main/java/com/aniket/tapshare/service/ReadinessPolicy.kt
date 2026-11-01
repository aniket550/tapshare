package com.aniket.tapshare.service

// Pure Kotlin (no android.* imports) so it runs in plain JVM unit tests.

enum class BluetoothStatus { UNSUPPORTED, OFF, TURNING_ON, ON, TURNING_OFF }

data class ScreenState(
    val interactive: Boolean,
    val locked: Boolean,
)

/** Everything that decides whether TapShare may use its radios right now. */
data class DeviceConditions(
    val sharingEnabled: Boolean,
    val permissionsGranted: Boolean,
    val bluetooth: BluetoothStatus,
    /** True only on Android 10–11, where BLE scans return nothing while Location is off. */
    val locationServicesNeeded: Boolean,
    val locationServicesOn: Boolean,
    val screen: ScreenState,
)

/** Something the user has to fix. Shown on Home with a button when possible. */
enum class Problem { BLUETOOTH_UNSUPPORTED, SHARING_OFF, MISSING_PERMISSIONS, BLUETOOTH_OFF, LOCATION_SERVICES_OFF }

/** Normal, expected pauses (battery rule: radios only while screen on + unlocked). */
enum class PauseReason { SCREEN_OFF, LOCKED }

sealed interface Readiness {
    /** Radios may run (Phase 1 starts BLE advertise + scan on this state). */
    data object Ready : Readiness
    data class Paused(val reason: PauseReason) : Readiness
    data class Blocked(val problem: Problem) : Readiness
}

object ReadinessPolicy {

    /** Order matters: the first failing check is the one we show to the user. */
    fun evaluate(c: DeviceConditions): Readiness = when {
        c.bluetooth == BluetoothStatus.UNSUPPORTED -> Readiness.Blocked(Problem.BLUETOOTH_UNSUPPORTED)
        !c.sharingEnabled -> Readiness.Blocked(Problem.SHARING_OFF)
        !c.permissionsGranted -> Readiness.Blocked(Problem.MISSING_PERMISSIONS)
        c.bluetooth != BluetoothStatus.ON -> Readiness.Blocked(Problem.BLUETOOTH_OFF)
        c.locationServicesNeeded && !c.locationServicesOn -> Readiness.Blocked(Problem.LOCATION_SERVICES_OFF)
        !c.screen.interactive -> Readiness.Paused(PauseReason.SCREEN_OFF)
        c.screen.locked -> Readiness.Paused(PauseReason.LOCKED)
        else -> Readiness.Ready
    }

    /**
     * Should the foreground service exist at all? It stays alive through pauses and a switched-off
     * Bluetooth (so it can resume instantly), but not when sharing is off or permissions are missing.
     */
    fun serviceShouldRun(sharingEnabled: Boolean, permissionsGranted: Boolean, bluetooth: BluetoothStatus): Boolean =
        sharingEnabled && permissionsGranted && bluetooth != BluetoothStatus.UNSUPPORTED
}

package com.aniket.tapshare.util

// Pure Kotlin (no android.* imports) so it runs in plain JVM unit tests.
// SDK numbers are written as ints on purpose: 29 = Android 10, 31 = 12, 32 = 12L, 33 = 13, 37 = 17.

/** How we show permissions to the user: one row per group, not one per Android permission. */
enum class PermissionGroup(val required: Boolean) {
    /** Bluetooth scan/advertise/connect + Wi-Fi + local network. One system dialog on Android 12+. */
    NEARBY_DEVICES(required = true),

    /** Only Android 10–12L, where BLE (≤ 11) and Wi-Fi (≤ 12L) scanning count as location. */
    LOCATION(required = true),

    /** Foreground-service notification. The service still runs if the user says no. */
    NOTIFICATIONS(required = false),

    /**
     * Android 10–11 only: BLE scan results while TapShare is closed need "Allow all the time".
     * Asked separately (Home → "When TapShare is closed"), never in the first-run screen —
     * Android rejects it in the same request as foreground location.
     */
    BACKGROUND_LOCATION(required = false),
}

/** Every runtime permission TapShare may ask for, and the Android versions where it applies. */
enum class AppPermission(
    val manifestName: String,
    val sdkRange: IntRange,
    val group: PermissionGroup,
) {
    FINE_LOCATION("android.permission.ACCESS_FINE_LOCATION", 29..32, PermissionGroup.LOCATION),

    // Android 12 rejects a FINE request that doesn't also ask for COARSE.
    COARSE_LOCATION("android.permission.ACCESS_COARSE_LOCATION", 29..32, PermissionGroup.LOCATION),

    BLUETOOTH_SCAN("android.permission.BLUETOOTH_SCAN", 31..Int.MAX_VALUE, PermissionGroup.NEARBY_DEVICES),
    BLUETOOTH_ADVERTISE("android.permission.BLUETOOTH_ADVERTISE", 31..Int.MAX_VALUE, PermissionGroup.NEARBY_DEVICES),
    BLUETOOTH_CONNECT("android.permission.BLUETOOTH_CONNECT", 31..Int.MAX_VALUE, PermissionGroup.NEARBY_DEVICES),
    NEARBY_WIFI_DEVICES("android.permission.NEARBY_WIFI_DEVICES", 33..Int.MAX_VALUE, PermissionGroup.NEARBY_DEVICES),
    ACCESS_LOCAL_NETWORK("android.permission.ACCESS_LOCAL_NETWORK", 37..Int.MAX_VALUE, PermissionGroup.NEARBY_DEVICES),

    POST_NOTIFICATIONS("android.permission.POST_NOTIFICATIONS", 33..Int.MAX_VALUE, PermissionGroup.NOTIFICATIONS),

    BACKGROUND_LOCATION("android.permission.ACCESS_BACKGROUND_LOCATION", 29..30, PermissionGroup.BACKGROUND_LOCATION),
}

/** What the UI can do about a permission (or a whole group). Ordered from best to worst. */
enum class GrantState {
    GRANTED,

    /** Never asked, or the user said no once: the system dialog can still be shown. */
    CAN_REQUEST,

    /** User said no and Android won't show the dialog again: only Settings can fix it. */
    BLOCKED,
}

data class GroupStatus(
    val group: PermissionGroup,
    val state: GrantState,
    /** Permissions in this group that are not granted yet. */
    val missing: List<AppPermission>,
)

data class PermissionSummary(val groups: List<GroupStatus>) {
    val allRequiredGranted: Boolean
        get() = groups.filter { it.group.required }.all { it.state == GrantState.GRANTED }

    /** Everything the first-run screen can still ask for (background location is asked elsewhere). */
    val requestable: List<AppPermission>
        get() = groups.filter { it.state == GrantState.CAN_REQUEST && it.group != PermissionGroup.BACKGROUND_LOCATION }
            .flatMap { it.missing }

    /** Groups shown on the first-run permission screen. */
    val onboardingGroups: List<GroupStatus>
        get() = groups.filter { it.group != PermissionGroup.BACKGROUND_LOCATION }

    val anyRequiredBlocked: Boolean
        get() = groups.any { it.group.required && it.state == GrantState.BLOCKED }

    fun stateOf(group: PermissionGroup): GrantState? = groups.firstOrNull { it.group == group }?.state
}

object PermissionPlan {

    fun permissionsFor(sdk: Int): List<AppPermission> =
        AppPermission.entries.filter { sdk in it.sdkRange }

    fun groupsFor(sdk: Int): List<PermissionGroup> =
        PermissionGroup.entries.filter { group -> permissionsFor(sdk).any { it.group == group } }

    /**
     * Android has no direct "permanently denied" API. The usual rule:
     * not granted + asked before + Android says "don't show rationale" ⇒ the dialog won't appear again.
     */
    fun grantState(granted: Boolean, askedBefore: Boolean, shouldShowRationale: Boolean): GrantState = when {
        granted -> GrantState.GRANTED
        !askedBefore -> GrantState.CAN_REQUEST
        shouldShowRationale -> GrantState.CAN_REQUEST
        else -> GrantState.BLOCKED
    }

    fun summarize(
        sdk: Int,
        isGranted: (AppPermission) -> Boolean,
        askedBefore: (AppPermission) -> Boolean,
        shouldShowRationale: (AppPermission) -> Boolean,
    ): PermissionSummary {
        val perms = permissionsFor(sdk)
        val groups = groupsFor(sdk).map { group ->
            val inGroup = perms.filter { it.group == group }
            val states = inGroup.associateWith {
                grantState(isGranted(it), askedBefore(it), shouldShowRationale(it))
            }
            GroupStatus(
                group = group,
                // A group is as bad as its worst permission.
                state = states.values.maxByOrNull { it.ordinal } ?: GrantState.GRANTED,
                missing = inGroup.filter { states[it] != GrantState.GRANTED },
            )
        }
        return PermissionSummary(groups)
    }
}

package com.aniket.tapshare.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aniket.tapshare.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Current permission state as a Flow. Android has no "permission changed" callback, so we
 * refresh on every Activity resume and after each permission dialog. (If the user revokes a
 * permission in Settings, Android kills our process, so a stale "granted" can't survive.)
 */
class PermissionsRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {
    val sdk: Int = Build.VERSION.SDK_INT

    private val _summary = MutableStateFlow(compute(activity = null))
    val summary: StateFlow<PermissionSummary> = _summary.asStateFlow()

    /** Covers both POST_NOTIFICATIONS (13+) and the user switching notifications off in Settings. */
    private val _notificationsEnabled = MutableStateFlow(readNotificationsEnabled())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    /** "Display over other apps" (special access, granted in Settings) — needed to work while closed. */
    private val _overlayAllowed = MutableStateFlow(Settings.canDrawOverlays(context))
    val overlayAllowed: StateFlow<Boolean> = _overlayAllowed.asStateFlow()

    /** Android 10–11 only: "Allow all the time" location, so BLE scans work while TapShare is closed. */
    val backgroundLocationNeeded: Boolean = sdk in AppPermission.BACKGROUND_LOCATION.sdkRange
    private val _backgroundLocationGranted = MutableStateFlow(readBackgroundLocation())
    val backgroundLocationGranted: StateFlow<Boolean> = _backgroundLocationGranted.asStateFlow()

    /** Pass the Activity when you have one: only it can tell us "should show rationale". */
    fun refresh(activity: Activity?) {
        _summary.value = compute(activity)
        _notificationsEnabled.value = readNotificationsEnabled()
        _overlayAllowed.value = Settings.canDrawOverlays(context)
        _backgroundLocationGranted.value = readBackgroundLocation()
    }

    private fun readBackgroundLocation(): Boolean =
        !backgroundLocationNeeded || isGranted(AppPermission.BACKGROUND_LOCATION)

    fun markRequested(permissions: Collection<AppPermission>) {
        settings.markPermissionsAsked(permissions.map { it.manifestName })
    }

    fun isGranted(permission: AppPermission): Boolean =
        ContextCompat.checkSelfPermission(context, permission.manifestName) == PackageManager.PERMISSION_GRANTED

    private fun compute(activity: Activity?): PermissionSummary = PermissionPlan.summarize(
        sdk = sdk,
        isGranted = ::isGranted,
        askedBefore = { settings.wasPermissionAsked(it.manifestName) },
        shouldShowRationale = { perm ->
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, perm.manifestName)
        },
    )

    private fun readNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}

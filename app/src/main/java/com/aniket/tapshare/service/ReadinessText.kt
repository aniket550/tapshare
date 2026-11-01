package com.aniket.tapshare.service

import androidx.annotation.StringRes
import com.aniket.tapshare.R

/** One place that maps a [Readiness] to user-facing text, shared by Home and the notification. */
data class ReadinessText(@StringRes val title: Int, @StringRes val body: Int)

fun Readiness.text(bluetooth: BluetoothStatus): ReadinessText = when (this) {
    Readiness.Ready -> ReadinessText(R.string.ready_title, R.string.ready_body)
    is Readiness.Paused -> when (reason) {
        PauseReason.SCREEN_OFF -> ReadinessText(R.string.paused_title, R.string.paused_screen_off_body)
        PauseReason.LOCKED -> ReadinessText(R.string.paused_title, R.string.paused_locked_body)
    }
    is Readiness.Blocked -> when (problem) {
        Problem.BLUETOOTH_UNSUPPORTED -> ReadinessText(R.string.unsupported_title, R.string.unsupported_body)
        Problem.SHARING_OFF -> ReadinessText(R.string.sharing_off_title, R.string.sharing_off_body)
        Problem.MISSING_PERMISSIONS -> ReadinessText(R.string.permissions_missing_title, R.string.permissions_missing_body)
        Problem.BLUETOOTH_OFF -> ReadinessText(
            R.string.bluetooth_off_title,
            if (bluetooth == BluetoothStatus.TURNING_ON) R.string.bluetooth_turning_on_body else R.string.bluetooth_off_body,
        )
        Problem.LOCATION_SERVICES_OFF -> ReadinessText(R.string.location_off_title, R.string.location_off_body)
    }
}

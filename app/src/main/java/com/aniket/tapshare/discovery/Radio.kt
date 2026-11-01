package com.aniket.tapshare.discovery

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback

/** Advert strength. Lower = the other phone must be closer to hear us (good for "tap" detection). */
enum class TxPower(val androidLevel: Int) {
    ULTRA_LOW(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW),
    LOW(AdvertiseSettings.ADVERTISE_TX_POWER_LOW),
    MEDIUM(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM),
    HIGH(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH),
}

/** Why a radio (advertising or scanning) is not running. Each one gets a plain-words message in the UI. */
enum class RadioFailure {
    /** This phone's Bluetooth chip can't send BLE adverts (some older phones). */
    NOT_SUPPORTED,
    BLUETOOTH_OFF,
    NO_PERMISSION,
    /** Other apps are using all advert slots. */
    TOO_MANY_ADVERTISERS,
    /** Android throttled us (more than 5 scan starts in 30 s). */
    TOO_FREQUENT,
    /** Bluetooth chip out of scan filter / scan slots. */
    OUT_OF_RESOURCES,
    INTERNAL,
}

sealed interface RadioStatus {
    data object Off : RadioStatus
    data object Starting : RadioStatus
    data object On : RadioStatus
    data class Failed(val reason: RadioFailure) : RadioStatus
}

internal fun advertiseFailure(code: Int): RadioFailure = when (code) {
    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> RadioFailure.NOT_SUPPORTED
    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> RadioFailure.TOO_MANY_ADVERTISERS
    else -> RadioFailure.INTERNAL // DATA_TOO_LARGE / ALREADY_STARTED / INTERNAL_ERROR: our bug or stack glitch
}

internal fun scanFailure(code: Int): RadioFailure = when (code) {
    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> RadioFailure.NOT_SUPPORTED
    ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> RadioFailure.TOO_FREQUENT
    ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> RadioFailure.OUT_OF_RESOURCES
    else -> RadioFailure.INTERNAL
}

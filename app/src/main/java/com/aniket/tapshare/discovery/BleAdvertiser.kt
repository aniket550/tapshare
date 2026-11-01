package com.aniket.tapshare.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.aniket.tapshare.util.TapLog

/** Sends our TapShare advert (UUID + TX power, random ID in the scan response). */
class BleAdvertiser(
    private val context: Context,
    private val onStatus: (RadioStatus) -> Unit,
) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val uuid = ParcelUuid.fromString(DiscoveryProtocol.SERVICE_UUID)
    private var callback: AdvertiseCallback? = null

    // Permission is checked at the top (hasPermission); lint can't see through the helper.
    @SuppressLint("MissingPermission")
    fun start(serviceData: ByteArray, txPower: TxPower, fast: Boolean) {
        stopInternal()
        if (!hasPermission()) return onStatus(RadioStatus.Failed(RadioFailure.NO_PERMISSION))
        val bt = adapter
        if (bt == null || !bt.isEnabled) return onStatus(RadioStatus.Failed(RadioFailure.BLUETOOTH_OFF))
        // null here (with Bluetooth on) means the chip has no BLE "peripheral" mode.
        val advertiser = bt.bluetoothLeAdvertiser
            ?: return onStatus(RadioStatus.Failed(RadioFailure.NOT_SUPPORTED))

        val settings = AdvertiseSettings.Builder()
            // ~10 adverts/s while the app is open (live numbers), ~1/s in the background (battery).
            .setAdvertiseMode(if (fast) AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY else AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(txPower.androidLevel)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val advert = AdvertiseData.Builder()
            .addServiceUuid(uuid)
            .setIncludeTxPowerLevel(true)
            .setIncludeDeviceName(false)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .addServiceData(uuid, serviceData)
            .setIncludeDeviceName(false)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                if (callback === this) {
                    TapLog.i("Advertiser: on (tx=$txPower, fast=$fast, data=${DiscoveryProtocol.idToHex(serviceData)})")
                    onStatus(RadioStatus.On)
                }
            }

            override fun onStartFailure(errorCode: Int) {
                if (callback === this) {
                    callback = null
                    TapLog.w("Advertiser: failed code=$errorCode")
                    onStatus(RadioStatus.Failed(advertiseFailure(errorCode)))
                }
            }
        }
        callback = cb
        onStatus(RadioStatus.Starting)
        try {
            advertiser.startAdvertising(settings, advert, scanResponse, cb)
        } catch (e: SecurityException) {
            callback = null
            onStatus(RadioStatus.Failed(RadioFailure.NO_PERMISSION))
        } catch (e: IllegalStateException) {
            // "BT Adapter is not turned ON" — Bluetooth switched off between our check and the call.
            callback = null
            onStatus(RadioStatus.Failed(RadioFailure.BLUETOOTH_OFF))
        }
    }

    fun stop() {
        stopInternal()
        onStatus(RadioStatus.Off)
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal() {
        val cb = callback ?: return
        callback = null
        try {
            if (hasPermission()) adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb)
        } catch (e: RuntimeException) {
            // Bluetooth already off: the advert is gone anyway.
            TapLog.d("Advertiser: stop ignored (${e.javaClass.simpleName})")
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
}

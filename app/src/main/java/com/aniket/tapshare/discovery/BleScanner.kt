package com.aniket.tapshare.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.aniket.tapshare.util.TapLog

/** Listens for TapShare adverts only (filter on our UUID, so the chip can drop everything else). */
class BleScanner(
    private val context: Context,
    private val onPeer: (id: String, rssi: Int, txPower: Int?, closeTo: String?, flags: Int) -> Unit,
    private val onStatus: (RadioStatus) -> Unit,
) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val uuid = ParcelUuid.fromString(DiscoveryProtocol.SERVICE_UUID)
    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun start(fast: Boolean) {
        stopInternal()
        if (!hasPermission()) return onStatus(RadioStatus.Failed(RadioFailure.NO_PERMISSION))
        val bt = adapter
        if (bt == null || !bt.isEnabled) return onStatus(RadioStatus.Failed(RadioFailure.BLUETOOTH_OFF))
        val scanner = bt.bluetoothLeScanner ?: return onStatus(RadioStatus.Failed(RadioFailure.BLUETOOTH_OFF))

        val filters = listOf(ScanFilter.Builder().setServiceUuid(uuid).build())
        val settings = ScanSettings.Builder()
            .setScanMode(if (fast) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // every packet → live RSSI
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (callback === this) handle(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                if (callback === this) results.forEach(::handle)
            }

            override fun onScanFailed(errorCode: Int) {
                if (callback === this) {
                    callback = null
                    TapLog.w("Scanner: failed code=$errorCode")
                    onStatus(RadioStatus.Failed(scanFailure(errorCode)))
                }
            }
        }
        callback = cb
        try {
            scanner.startScan(filters, settings, cb)
            TapLog.i("Scanner: on (fast=$fast)")
            onStatus(RadioStatus.On)
        } catch (e: SecurityException) {
            callback = null
            onStatus(RadioStatus.Failed(RadioFailure.NO_PERMISSION))
        } catch (e: IllegalStateException) {
            callback = null
            onStatus(RadioStatus.Failed(RadioFailure.BLUETOOTH_OFF))
        }
    }

    fun stop() {
        stopInternal()
        onStatus(RadioStatus.Off)
    }

    private fun handle(result: ScanResult) {
        val record = result.scanRecord
        // The ID is in the scan response. If a phone's scan missed it, fall back to the
        // (also randomised) Bluetooth address so the phone still shows up.
        val parsed = DiscoveryProtocol.parseServiceData(record?.getServiceData(uuid))
        val id = parsed?.id
            ?: runCatching { "MAC-" + result.device.address.replace(":", "") }.getOrNull()
            ?: return
        val tx = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
        onPeer(id, result.rssi, tx, parsed?.closeToPrefix, parsed?.flags ?: 0)
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal() {
        val cb = callback ?: return
        callback = null
        try {
            if (hasPermission()) adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (e: RuntimeException) {
            TapLog.d("Scanner: stop ignored (${e.javaClass.simpleName})")
        }
    }

    /** Android 12+: BLUETOOTH_SCAN. Android 10–11: BLE scan results need fine location. */
    private fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}

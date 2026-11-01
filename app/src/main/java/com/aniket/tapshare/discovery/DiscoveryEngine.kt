package com.aniket.tapshare.discovery

import android.content.Context
import android.os.SystemClock
import com.aniket.tapshare.data.SettingsStore
import com.aniket.tapshare.util.TapLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** Everything the Nearby Phones screen needs, in one immutable value. */
data class DiscoveryState(
    val running: Boolean,
    /** App on screen → fast adverts + fast scanning (live numbers). Background → low power. */
    val fast: Boolean,
    val myId: String,
    val advertising: RadioStatus,
    val scanning: RadioStatus,
    val txPower: TxPower,
    val peers: List<PeerSnapshot>,
)

/**
 * Runs BLE advertising + scanning together. Started/stopped by TapShareService from
 * [com.aniket.tapshare.service.Readiness] (only while screen on + unlocked + Bluetooth on).
 * All calls must come from the main thread (service, activity and BLE callbacks all do).
 */
class DiscoveryEngine(
    context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val random = SecureRandom()
    private val rotatingId = RotatingId(
        now = SystemClock::elapsedRealtime,
        randomBytes = { n -> ByteArray(n).also(random::nextBytes) },
    )
    private val peers = PeerTable()
    private val throttle = ScanThrottle()

    private val advertiser = BleAdvertiser(context) { s -> _state.update { it.copy(advertising = s) } }
    private val scanner = BleScanner(context, onPeer = ::onPeer) { s -> _state.update { it.copy(scanning = s) } }

    private var running = false
    private var fast = false
    private var rotationJob: Job? = null
    private var scanStartJob: Job? = null
    private var tickerJob: Job? = null
    private var lastPublish = 0L

    private val _state = MutableStateFlow(
        DiscoveryState(
            running = false,
            fast = false,
            myId = DiscoveryProtocol.idToHex(rotatingId.current()),
            advertising = RadioStatus.Off,
            scanning = RadioStatus.Off,
            txPower = settings.txPower.value,
            peers = emptyList(),
        ),
    )
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    fun start() {
        if (running) return
        running = true
        TapLog.i("Discovery: start")
        _state.update { it.copy(running = true) }
        startAdvertising()
        requestScanStart()
        scheduleRotation()
        updateTicker()
    }

    fun stop() {
        if (!running) return
        running = false
        TapLog.i("Discovery: stop")
        rotationJob?.cancel()
        scanStartJob?.cancel()
        tickerJob?.cancel()
        advertiser.stop()
        scanner.stop()
        peers.clear()
        _state.update { it.copy(running = false, peers = emptyList()) }
    }

    /** Called when TapShare's UI becomes visible / hidden. */
    fun setForeground(visible: Boolean) {
        if (fast == visible) return
        fast = visible
        _state.update { it.copy(fast = visible) }
        if (running) {
            startAdvertising()
            requestScanStart()
        }
        updateTicker()
    }

    fun setTxPower(txPower: TxPower) {
        settings.setTxPower(txPower)
        _state.update { it.copy(txPower = txPower) }
        if (running) startAdvertising()
    }

    /** Fresh min/max/avg for the next calibration try. */
    fun resetStats() {
        peers.resetStats(now())
        publishPeers()
    }

    private fun startAdvertising() {
        val id = rotatingId.current()
        _state.update { it.copy(myId = DiscoveryProtocol.idToHex(id)) }
        advertiser.start(id, settings.txPower.value, fast)
    }

    /** Starts the scan now, or as soon as Android's "5 starts per 30 s" limit allows. */
    private fun requestScanStart() {
        scanStartJob?.cancel()
        val wait = throttle.delayBeforeNextStart(now())
        if (wait == 0L) {
            throttle.recordStart(now())
            scanner.start(fast)
            return
        }
        TapLog.i("Scanner: waiting ${wait}ms to stay under Android's scan limit")
        _state.update { it.copy(scanning = RadioStatus.Starting) }
        scanStartJob = scope.launch {
            delay(wait)
            if (running) {
                throttle.recordStart(now())
                scanner.start(fast)
            }
        }
    }

    /** New random ID every 15 minutes, so nobody can follow one phone around. */
    private fun scheduleRotation() {
        rotationJob?.cancel()
        rotationJob = scope.launch {
            while (isActive) {
                delay(rotatingId.msUntilRotation().coerceAtLeast(1_000))
                if (!running) break
                rotatingId.rotate()
                TapLog.i("Discovery: ID rotated")
                startAdvertising()
            }
        }
    }

    /** Refreshes "seen x s ago" and drops phones that went away — only while the UI is visible. */
    private fun updateTicker() {
        tickerJob?.cancel()
        if (!running || !fast) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                publishPeers()
            }
        }
    }

    private fun onPeer(id: String, rssi: Int, txPower: Int?) {
        if (!running) return
        val now = now()
        val isNew = peers.snapshot(now).none { it.id == id }
        peers.onSample(id, rssi, txPower, now)
        if (isNew) TapLog.i("Discovery: found ${DiscoveryProtocol.shortLabel(id)} rssi=$rssi tx=$txPower")
        if (isNew || now - lastPublish >= MIN_PUBLISH_GAP_MS) publishPeers()
    }

    private fun publishPeers() {
        val now = now()
        peers.prune(now)
        lastPublish = now
        _state.update { it.copy(peers = peers.snapshot(now)) }
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val TICK_MS = 500L
        /** At most ~5 UI updates per second, however many packets arrive. */
        const val MIN_PUBLISH_GAP_MS = 200L
    }
}

package com.aniket.tapshare.discovery

import android.content.Context
import android.os.SystemClock
import com.aniket.tapshare.data.SettingsStore
import com.aniket.tapshare.util.TapLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom

sealed interface CalibrationStatus {
    data object Idle : CalibrationStatus
    data class Running(val samples: Int) : CalibrationStatus
    data class Done(val enterLossDb: Double) : CalibrationStatus
    data object Failed : CalibrationStatus
}

/** Everything the screens need about discovery, in one immutable value. */
data class DiscoveryState(
    /** Radios actually on (wanted by the service and not paused for a share session). */
    val running: Boolean,
    /** App on screen → fast adverts + fast scanning. Background → low power. */
    val fast: Boolean,
    val myId: String,
    val advertising: RadioStatus,
    val scanning: RadioStatus,
    val txPower: TxPower,
    val peers: List<PeerSnapshot>,
    /** Phone currently touching ours (before it counts as a tap), for "hold them together" hints. */
    val touchingPeer: String?,
    val tapEnterLossDb: Double,
    val calibration: CalibrationStatus,
)

sealed interface DiscoveryEvent {
    /** Both phones touched long enough (mutual) — start a share session with [peerId]. */
    data class Tap(val peerId: String) : DiscoveryEvent

    /** [peerId] was touching and has now been pulled apart. */
    data class Apart(val peerId: String) : DiscoveryEvent
}

/**
 * Runs BLE advertising + scanning and turns signal strength into "tap" events.
 *
 * Tap rule (both must be true on BOTH phones):
 *  1. the other phone's path loss stayed under the threshold for [TapConfig.holdMs], and
 *  2. the other phone's advert says it sees US as touching (closeTo == our ID prefix).
 * If the other phone already triggered with us (FLAG_TRIGGERED), we follow as soon as it is near,
 * so both phones start together.
 *
 * All calls must come from the main thread (service, activity, session and BLE callbacks do).
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
    private val detector = TapDetector(TapConfig(enterLossDb = settings.tapEnterLossDb.value))

    private val advertiser = BleAdvertiser(context) { s -> _state.update { it.copy(advertising = s) } }
    private val scanner = BleScanner(context, onPeer = ::onPeer) { s -> _state.update { it.copy(scanning = s) } }

    /** TapShareService wants radios on (Readiness.Ready). */
    private var wanted = false

    /** A share session paused the radios (Nearby Connections needs Bluetooth to itself). */
    private var held = false
    private var radiosOn = false

    /** Fast adverts + scans in effect (app visible, or a short burst because a phone came near). */
    private var fast = false
    private var foreground = false
    private var bursting = false
    private var lastBurstEnd = 0L
    private var burstJob: Job? = null
    private var tapsEnabled = false

    /** Phone we currently advertise as "touching" (closeTo field). */
    private var closeTo: String? = null

    /** Phone we started a session with (FLAG_TRIGGERED in our advert). */
    private var triggeredWith: String? = null

    private var calibrator: TapCalibrator? = null
    private var rotationJob: Job? = null
    private var scanStartJob: Job? = null
    private var tickerJob: Job? = null
    private var calibrationJob: Job? = null
    private var lastPublish = 0L

    private val _events = MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DiscoveryEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(
        DiscoveryState(
            running = false,
            fast = false,
            myId = DiscoveryProtocol.idToHex(rotatingId.current()),
            advertising = RadioStatus.Off,
            scanning = RadioStatus.Off,
            txPower = settings.txPower.value,
            peers = emptyList(),
            touchingPeer = null,
            tapEnterLossDb = settings.tapEnterLossDb.value,
            calibration = CalibrationStatus.Idle,
        ),
    )
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    /** Our current random ID (Nearby endpoint name + role choice use it). */
    val myId: String get() = _state.value.myId

    // ---------------------------------------------------------------- control

    fun start() {
        wanted = true
        applyRadios()
    }

    fun stop() {
        wanted = false
        applyRadios()
    }

    /** Pause radios for a share session (keeps all state). */
    fun hold() {
        held = true
        applyRadios()
    }

    fun release() {
        held = false
        applyRadios()
    }

    /** Only the visible app reacts to taps (v1). Sessions turn it off while they run. */
    fun setTapsEnabled(enabled: Boolean) {
        tapsEnabled = enabled
    }

    /** Session finished: stop telling the other phone we're triggered. The tap stays disarmed until apart. */
    fun endSession() {
        if (triggeredWith == null) return
        triggeredWith = null
        if (radiosOn) startAdvertising()
    }

    fun setForeground(visible: Boolean) {
        if (foreground == visible) return
        foreground = visible
        applySpeed()
    }

    /** Switch adverts/scans between fast and low-power when needed (each switch restarts the scan). */
    private fun applySpeed() {
        val shouldBeFast = foreground || bursting
        if (shouldBeFast == fast) return
        fast = shouldBeFast
        _state.update { it.copy(fast = shouldBeFast) }
        if (radiosOn) {
            startAdvertising()
            requestScanStart()
        }
        updateTicker()
    }

    /**
     * App closed: when a TapShare phone gets near, go fast for up to 10 s so a tap is detected in
     * about a second (battery rule: LOW_LATENCY only in short bursts). At most one burst per 20 s
     * keeps us under Android's 5-scan-starts-per-30 s limit.
     */
    private fun maybeBurst(loss: Double, nowMs: Long) {
        if (foreground || bursting || !radiosOn) return
        if (loss > detector.config.exitLossDb + BURST_EXTRA_DB) return
        if (nowMs - lastBurstEnd < BURST_COOLDOWN_MS) return
        TapLog.i("Discovery: phone near while closed, fast burst")
        bursting = true
        applySpeed()
        burstJob?.cancel()
        burstJob = scope.launch {
            delay(BURST_MS)
            bursting = false
            lastBurstEnd = now()
            applySpeed()
        }
    }

    fun setTxPower(txPower: TxPower) {
        settings.setTxPower(txPower)
        _state.update { it.copy(txPower = txPower) }
        if (radiosOn) startAdvertising()
    }

    fun resetStats() {
        peers.resetStats(now())
        publishPeers()
    }

    /** Hold the phones together for 3 s: the threshold is set from what this pair of phones measures. */
    fun startCalibration() {
        if (!radiosOn) {
            _state.update { it.copy(calibration = CalibrationStatus.Failed) }
            return
        }
        calibrationJob?.cancel()
        calibrator = TapCalibrator()
        _state.update { it.copy(calibration = CalibrationStatus.Running(0)) }
        calibrationJob = scope.launch {
            delay(CALIBRATION_MS)
            val result = calibrator?.result()
            calibrator = null
            if (result == null) {
                _state.update { it.copy(calibration = CalibrationStatus.Failed) }
            } else {
                setTapEnterLoss(result)
                _state.update { it.copy(calibration = CalibrationStatus.Done(result)) }
                TapLog.i("Discovery: calibrated enter loss = $result dB")
            }
        }
    }

    fun resetCalibration() {
        setTapEnterLoss(TapConfig().enterLossDb)
        _state.update { it.copy(calibration = CalibrationStatus.Idle) }
    }

    private fun setTapEnterLoss(db: Double) {
        settings.setTapEnterLossDb(db)
        detector.config = detector.config.copy(enterLossDb = db)
        _state.update { it.copy(tapEnterLossDb = db) }
    }

    // ---------------------------------------------------------------- radios

    private fun applyRadios() {
        val shouldRun = wanted && !held
        if (shouldRun == radiosOn) return
        radiosOn = shouldRun
        _state.update { it.copy(running = shouldRun) }
        if (shouldRun) {
            TapLog.i("Discovery: radios on")
            startAdvertising()
            requestScanStart()
            scheduleRotation()
        } else {
            TapLog.i("Discovery: radios off")
            rotationJob?.cancel()
            scanStartJob?.cancel()
            burstJob?.cancel()
            bursting = false
            fast = foreground
            advertiser.stop()
            scanner.stop()
            peers.clear()
            // Keep the tap detector while a session holds the radios: it remembers "disarmed until
            // pulled apart", so phones still touching after a share don't start a second one.
            if (!wanted) detector.clear()
            closeTo = null
            _state.update { it.copy(peers = emptyList(), touchingPeer = null) }
        }
        updateTicker()
    }

    private fun startAdvertising() {
        val id = rotatingId.current()
        _state.update { it.copy(myId = DiscoveryProtocol.idToHex(id)) }
        val flags = if (triggeredWith != null) DiscoveryProtocol.FLAG_TRIGGERED else 0
        val data = DiscoveryProtocol.encodeServiceData(id, triggeredWith ?: closeTo, flags)
        advertiser.start(data, settings.txPower.value, fast)
    }

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
            if (radiosOn) {
                throttle.recordStart(now())
                scanner.start(fast)
            }
        }
    }

    /** New random ID every 15 minutes — but never in the middle of a tap/session. */
    private fun scheduleRotation() {
        rotationJob?.cancel()
        rotationJob = scope.launch {
            while (isActive) {
                delay(rotatingId.msUntilRotation().coerceAtLeast(1_000))
                if (!radiosOn) break
                if (triggeredWith != null || closeTo != null) {
                    delay(30_000)
                    continue
                }
                rotatingId.rotate()
                TapLog.i("Discovery: ID rotated")
                startAdvertising()
            }
        }
    }

    private fun updateTicker() {
        tickerJob?.cancel()
        if (!radiosOn || !fast) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                publishPeers()
            }
        }
    }

    // ---------------------------------------------------------------- packets → taps

    private fun onPeer(id: String, rssi: Int, txPower: Int?, peerCloseTo: String?, flags: Int) {
        if (!radiosOn) return
        val now = now()
        val isNew = peers.smoothedOf(id) == null
        peers.onSample(id, rssi, txPower, now, peerCloseTo, flags)
        if (isNew) TapLog.i("Discovery: found ${DiscoveryProtocol.shortLabel(id)} rssi=$rssi tx=$txPower")

        val loss = pathLoss(peers.smoothedOf(id) ?: rssi.toDouble(), txPower)
        maybeBurst(loss, now)
        if (detector.update(id, loss, now)) {
            TapLog.i("Discovery: ${DiscoveryProtocol.shortLabel(id)} pulled apart (loss=${loss.toInt()} dB)")
            _events.tryEmit(DiscoveryEvent.Apart(id))
        }
        calibrator?.let { c ->
            if (detector.closestTouching() == id || detector.lossOf(id)?.let { it < detector.config.exitLossDb } == true) {
                c.add(loss)
                _state.update { it.copy(calibration = CalibrationStatus.Running(c.count())) }
            }
        }

        updateCloseTo()
        checkTap(id, peerCloseTo, flags, now)

        if (isNew || now - lastPublish >= MIN_PUBLISH_GAP_MS) publishPeers()
    }

    /** Tell nearby phones which phone we are touching (only re-advertise when it changes). */
    private fun updateCloseTo() {
        if (triggeredWith != null) return
        val touching = detector.closestTouching()
        if (touching != closeTo) {
            closeTo = touching
            _state.update { it.copy(touchingPeer = touching) }
            if (radiosOn) startAdvertising()
        }
    }

    private fun checkTap(id: String, peerCloseTo: String?, flags: Int, now: Long) {
        if (!tapsEnabled || triggeredWith != null) return
        val seesMe = peerCloseTo != null && peerCloseTo == DiscoveryProtocol.prefixOf(myId)
        if (!seesMe) return
        val peerTriggered = (flags and DiscoveryProtocol.FLAG_TRIGGERED) != 0
        if (detector.isHeld(id, now) || (peerTriggered && detector.isNear(id))) {
            triggeredWith = id
            detector.disarm(id)
            TapLog.i("Discovery: TAP with ${DiscoveryProtocol.shortLabel(id)} (loss=${detector.lossOf(id)?.toInt()} dB, followed=$peerTriggered)")
            startAdvertising() // now carries FLAG_TRIGGERED so the other phone starts too
            _events.tryEmit(DiscoveryEvent.Tap(id))
        }
    }

    private fun publishPeers() {
        val now = now()
        peers.prune(now)
        detector.forgetOlderThan(now, 10_000)
        lastPublish = now
        _state.update { it.copy(peers = peers.snapshot(now)) }
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val TICK_MS = 500L
        const val MIN_PUBLISH_GAP_MS = 200L
        const val CALIBRATION_MS = 3_000L
        const val BURST_MS = 10_000L
        const val BURST_COOLDOWN_MS = 20_000L

        /** "Near" for a burst = within the touching exit band + this much (≈ an arm's length). */
        const val BURST_EXTRA_DB = 12.0
    }
}

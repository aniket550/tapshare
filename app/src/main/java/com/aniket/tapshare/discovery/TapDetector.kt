package com.aniket.tapshare.discovery

// Pure Kotlin (no android.* imports) so it runs in plain JVM unit tests.

/**
 * Tuning for "are the phones touching?".
 *
 * We compare PATH LOSS, not raw RSSI: pathLoss = (TX power the other phone says it uses) − RSSI.
 * That way two phones with different TX settings still agree. From Aniket's phones (Ultra low,
 * TX ≈ −21 dBm): touching ≈ −43 dBm → loss ≈ 22 dB; 1 m ≈ −80 dBm → loss ≈ 59 dB.
 */
data class TapConfig(
    /** Loss at or below this = "touching". Default sits between touching (~22) and 30 cm (~40). */
    val enterLossDb: Double = 32.0,
    /** Must rise this much above [enterLossDb] to count as "pulled apart" (hysteresis, no flicker). */
    val exitMarginDb: Double = 8.0,
    /** How long the phones must stay together before it counts as a tap (iOS: "hold for a moment"). */
    val holdMs: Long = 600,
    /** At least this many packets while close (one lucky packet isn't a tap). */
    val minSamples: Int = 3,
) {
    val exitLossDb: Double get() = enterLossDb + exitMarginDb
}

/** Android's advertised TX power per setting is used when a phone doesn't send one. */
const val DEFAULT_PEER_TX_DBM = -21

fun pathLoss(rssi: Double, peerTxDbm: Int?): Double = (peerTxDbm ?: DEFAULT_PEER_TX_DBM) - rssi

/**
 * Per-phone "touching" state with hysteresis. Feed it every smoothed sample; ask it which phone
 * is close / held. After a tap the phone is disarmed until the two phones are pulled apart,
 * so one long touch can't start two sessions.
 */
class TapDetector(var config: TapConfig = TapConfig()) {

    private class State(
        var lastLoss: Double,
        var closeSince: Long? = null,
        var closeSamples: Int = 0,
        var armed: Boolean = true,
        var lastSeen: Long,
    )

    private val states = HashMap<String, State>()

    /** Returns true if this sample changed the phone from close to apart (useful to cancel). */
    @Synchronized
    fun update(peerId: String, loss: Double, nowMs: Long): Boolean {
        val s = states.getOrPut(peerId) { State(lastLoss = loss, lastSeen = nowMs) }
        s.lastLoss = loss
        s.lastSeen = nowMs
        return when {
            loss <= config.enterLossDb -> {
                if (s.closeSince == null) {
                    s.closeSince = nowMs
                    s.closeSamples = 1
                } else {
                    s.closeSamples++
                }
                false
            }
            loss > config.exitLossDb -> {
                val wasClose = s.closeSince != null
                s.closeSince = null
                s.closeSamples = 0
                s.armed = true
                wasClose
            }
            else -> false // in the hysteresis band: keep whatever state we had
        }
    }

    /** The closest phone that is currently "touching" (for the closeTo field), or null. */
    @Synchronized
    fun closestTouching(): String? = states.entries
        .filter { it.value.closeSince != null }
        .minByOrNull { it.value.lastLoss }
        ?.key

    /** True if [peerId] has been touching long enough to count as a tap and isn't disarmed. */
    @Synchronized
    fun isHeld(peerId: String, nowMs: Long): Boolean {
        val s = states[peerId] ?: return false
        val since = s.closeSince ?: return false
        return s.armed && s.closeSamples >= config.minSamples && nowMs - since >= config.holdMs
    }

    /** True if [peerId] is at least near (inside the exit band) — used to follow the other phone's trigger. */
    @Synchronized
    fun isNear(peerId: String): Boolean {
        val s = states[peerId] ?: return false
        return s.armed && s.lastLoss <= config.exitLossDb
    }

    @Synchronized
    fun isApart(peerId: String): Boolean {
        val s = states[peerId] ?: return true
        return s.lastLoss > config.exitLossDb
    }

    /** After a tap: ignore this phone until the two phones have been pulled apart. */
    @Synchronized
    fun disarm(peerId: String) {
        states[peerId]?.let {
            it.armed = false
            it.closeSince = null
            it.closeSamples = 0
        }
    }

    @Synchronized
    fun lossOf(peerId: String): Double? = states[peerId]?.lastLoss

    /** Drops phones not heard for a while — except disarmed ones, which must be seen apart first. */
    @Synchronized
    fun forgetOlderThan(nowMs: Long, maxAgeMs: Long) {
        states.entries.removeAll { it.value.armed && nowMs - it.value.lastSeen > maxAgeMs }
    }

    @Synchronized
    fun clear() = states.clear()
}

/**
 * "Calibrate" button: collect path loss for a few seconds while the phones touch, then put the
 * threshold a bit above the typical (median) touching loss, so this exact pair of phones works.
 */
class TapCalibrator(private val marginDb: Double = 8.0) {
    private val samples = ArrayList<Double>()

    @Synchronized
    fun add(loss: Double) {
        samples.add(loss)
    }

    @Synchronized
    fun count(): Int = samples.size

    /** New enter threshold, or null if we didn't get enough packets to trust it. */
    @Synchronized
    fun result(minSamples: Int = 8): Double? {
        if (samples.size < minSamples) return null
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        return (median + marginDb).coerceIn(MIN_ENTER_DB, MAX_ENTER_DB)
    }

    companion object {
        const val MIN_ENTER_DB = 15.0
        const val MAX_ENTER_DB = 50.0
    }
}

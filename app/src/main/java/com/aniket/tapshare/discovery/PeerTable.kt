package com.aniket.tapshare.discovery

// Pure Kotlin (no android.* imports) so it runs in plain JVM unit tests.

/** What the Nearby Phones screen shows for one other phone. */
data class PeerSnapshot(
    val id: String,
    /** Last raw RSSI in dBm (closer = less negative, e.g. -40 is closer than -70). */
    val lastRssi: Int,
    /** Smoothed RSSI (exponential moving average) — raw BLE RSSI jumps a lot between packets. */
    val smoothedRssi: Double,
    val minRssi: Int,
    val maxRssi: Int,
    val avgRssi: Double,
    val samples: Int,
    /** TX power the other phone says it uses (dBm), if it sent one. */
    val txPower: Int?,
    val samplesPerSecond: Double,
    val msSinceLastSeen: Long,
    /** Prefix of the phone this peer says it is touching (null = nobody). */
    val closeToPrefix: String? = null,
    val flags: Int = 0,
) {
    /** TX power − RSSI: comparable between phones with different TX settings. */
    val pathLoss: Double get() = pathLoss(smoothedRssi, txPower)
}

/**
 * Keeps live signal numbers for every TapShare phone we hear. Old entries disappear after
 * [expiryMs] without packets (phone left, screen off, or its random ID changed).
 */
class PeerTable(
    private val alpha: Double = 0.3,
    private val expiryMs: Long = 8_000,
) {
    private class Entry(
        var lastRssi: Int,
        var smoothed: Double,
        var min: Int,
        var max: Int,
        var sum: Long,
        var samples: Int,
        var txPower: Int?,
        var statsSince: Long,
        var lastSeen: Long,
        var closeTo: String? = null,
        var flags: Int = 0,
    )

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun onSample(id: String, rssi: Int, txPower: Int?, nowMs: Long, closeTo: String? = null, flags: Int = 0) {
        val e = entries[id]
        if (e == null) {
            entries[id] = Entry(rssi, rssi.toDouble(), rssi, rssi, rssi.toLong(), 1, txPower, nowMs, nowMs, closeTo, flags)
            return
        }
        e.closeTo = closeTo
        e.flags = flags
        e.lastRssi = rssi
        e.smoothed = alpha * rssi + (1 - alpha) * e.smoothed
        e.min = minOf(e.min, rssi)
        e.max = maxOf(e.max, rssi)
        e.sum += rssi
        e.samples += 1
        if (txPower != null) e.txPower = txPower
        e.lastSeen = nowMs
    }

    /** Removes phones not heard for [expiryMs]. Returns true if anything was removed. */
    @Synchronized
    fun prune(nowMs: Long): Boolean {
        val before = entries.size
        entries.entries.removeAll { nowMs - it.value.lastSeen > expiryMs }
        return entries.size != before
    }

    /** Starts min/max/avg fresh (used between calibration tries). Smoothed value is kept. */
    @Synchronized
    fun resetStats(nowMs: Long) {
        for (e in entries.values) {
            e.min = e.lastRssi
            e.max = e.lastRssi
            e.sum = e.lastRssi.toLong()
            e.samples = 1
            e.statsSince = nowMs
        }
    }

    @Synchronized
    fun clear() = entries.clear()

    /** Current smoothed RSSI of one phone (null if unknown). */
    @Synchronized
    fun smoothedOf(id: String): Double? = entries[id]?.smoothed

    /** Strongest (closest) phone first. */
    @Synchronized
    fun snapshot(nowMs: Long): List<PeerSnapshot> = entries
        .filter { nowMs - it.value.lastSeen <= expiryMs }
        .map { (id, e) ->
            val seconds = ((nowMs - e.statsSince).coerceAtLeast(1)) / 1000.0
            PeerSnapshot(
                id = id,
                lastRssi = e.lastRssi,
                smoothedRssi = e.smoothed,
                minRssi = e.min,
                maxRssi = e.max,
                avgRssi = e.sum.toDouble() / e.samples,
                samples = e.samples,
                txPower = e.txPower,
                samplesPerSecond = if (e.samples > 1) (e.samples - 1) / seconds else 0.0,
                msSinceLastSeen = nowMs - e.lastSeen,
                closeToPrefix = e.closeTo,
                flags = e.flags,
            )
        }
        .sortedByDescending { it.smoothedRssi }
}

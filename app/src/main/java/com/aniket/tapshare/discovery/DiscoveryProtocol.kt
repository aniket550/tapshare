package com.aniket.tapshare.discovery

// Pure Kotlin (no android.* imports) so it runs in plain JVM unit tests.

/**
 * What a TapShare phone puts in the air.
 *
 * Legacy BLE adverts carry at most 31 bytes, so the data is split:
 *  - advert:        flags (3) + our 128-bit service UUID (18) + TX power level (3) = 24 bytes
 *  - scan response: service data = our UUID (16) + random ID (8) + header (2)      = 26 bytes
 * No name, number or email ever goes out — only the random ID, which changes every 15 minutes.
 */
object DiscoveryProtocol {
    /** Random v4 UUID that identifies TapShare adverts (generated once for this app). */
    const val SERVICE_UUID = "4662e9a2-a885-4a8b-a6ee-36d4325952db"

    const val ID_BYTES = 8

    /** How long one random ID is used before a new one is made (privacy: can't be tracked for long). */
    const val ID_ROTATION_MS = 15 * 60 * 1000L

    fun idToHex(id: ByteArray): String = id.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    /** Returns null if the bytes are not a valid TapShare ID. */
    fun parseId(serviceData: ByteArray?): String? =
        if (serviceData != null && serviceData.size == ID_BYTES) idToHex(serviceData) else null

    /** "A1B2C3D4E5F60718" → "A1B2·C3D4" (short and readable on screen). */
    fun shortLabel(hexId: String): String =
        if (hexId.length >= 8) "${hexId.substring(0, 4)}·${hexId.substring(4, 8)}" else hexId
}

/** Holds our current random ID and swaps it for a new one every [rotationMs]. */
class RotatingId(
    private val now: () -> Long,
    private val randomBytes: (Int) -> ByteArray,
    private val rotationMs: Long = DiscoveryProtocol.ID_ROTATION_MS,
) {
    private var id: ByteArray = randomBytes(DiscoveryProtocol.ID_BYTES)
    private var createdAt: Long = now()

    /** Current ID; makes a new one if the old one is too old. */
    @Synchronized
    fun current(): ByteArray {
        if (now() - createdAt >= rotationMs) rotate()
        return id.copyOf()
    }

    @Synchronized
    fun msUntilRotation(): Long = (rotationMs - (now() - createdAt)).coerceAtLeast(0)

    @Synchronized
    fun rotate() {
        id = randomBytes(DiscoveryProtocol.ID_BYTES)
        createdAt = now()
    }
}

/**
 * Android (7+) silently stops delivering scan results if an app starts scanning more than
 * 5 times in 30 seconds (error SCANNING_TOO_FREQUENTLY is not even reported). We stay under it.
 */
class ScanThrottle(
    private val maxStarts: Int = 4, // one below Android's limit of 5, as safety margin
    private val windowMs: Long = 30_000,
) {
    private val starts = ArrayDeque<Long>()

    /** How long to wait before the next startScan is safe (0 = start now). */
    @Synchronized
    fun delayBeforeNextStart(nowMs: Long): Long {
        while (starts.isNotEmpty() && nowMs - starts.first() >= windowMs) starts.removeFirst()
        return if (starts.size < maxStarts) 0 else windowMs - (nowMs - starts.first())
    }

    @Synchronized
    fun recordStart(nowMs: Long) {
        starts.addLast(nowMs)
    }
}

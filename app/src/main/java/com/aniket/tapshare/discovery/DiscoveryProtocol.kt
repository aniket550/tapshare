package com.aniket.tapshare.discovery

// Pure Kotlin (no android.* imports) so it runs in plain JVM unit tests.

/**
 * What a TapShare phone puts in the air.
 *
 * Legacy BLE adverts carry at most 31 bytes, so the data is split:
 *  - advert:        flags (3) + our 128-bit service UUID (18) + TX power level (3) = 24 bytes
 *  - scan response: service data = header (2) + UUID (16) + [ID (8), closeTo (4), flags (1)] = 31 bytes
 *
 * closeTo = first 4 bytes of the ID of the phone we are touching right now (zeros = nobody).
 * That is how each phone learns "the other one also sees me close" (mutual tap) without connecting.
 * No name, number or email ever goes out — only random IDs, which change every 15 minutes.
 */
object DiscoveryProtocol {
    /** Random v4 UUID that identifies TapShare adverts (generated once for this app). */
    const val SERVICE_UUID = "4662e9a2-a885-4a8b-a6ee-36d4325952db"

    const val ID_BYTES = 8

    /** How long one random ID is used before a new one is made (privacy: can't be tracked for long). */
    const val ID_ROTATION_MS = 15 * 60 * 1000L

    const val PREFIX_BYTES = 4

    /** Flag bit: "I already started a share session with the phone in closeTo". */
    const val FLAG_TRIGGERED = 0x01

    fun idToHex(id: ByteArray): String = id.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    /** First 4 bytes of an ID as hex (8 chars) — what goes into closeTo. */
    fun prefixOf(hexId: String): String = hexId.take(PREFIX_BYTES * 2)

    /** Service data we put in the scan response. */
    fun encodeServiceData(id: ByteArray, closeToHexId: String?, flags: Int): ByteArray {
        val out = ByteArray(ID_BYTES + PREFIX_BYTES + 1)
        id.copyInto(out, 0, 0, ID_BYTES)
        if (closeToHexId != null) hexToBytes(prefixOf(closeToHexId)).copyInto(out, ID_BYTES)
        out[ID_BYTES + PREFIX_BYTES] = flags.toByte()
        return out
    }

    data class Parsed(val id: String, val closeToPrefix: String?, val flags: Int)

    /** Accepts the old 8-byte format (ID only) and the full 13-byte one. Null if not TapShare data. */
    fun parseServiceData(data: ByteArray?): Parsed? {
        if (data == null) return null
        return when (data.size) {
            ID_BYTES -> Parsed(idToHex(data), null, 0)
            ID_BYTES + PREFIX_BYTES + 1 -> {
                val prefix = data.copyOfRange(ID_BYTES, ID_BYTES + PREFIX_BYTES)
                Parsed(
                    id = idToHex(data.copyOfRange(0, ID_BYTES)),
                    closeToPrefix = if (prefix.all { it == 0.toByte() }) null else idToHex(prefix),
                    flags = data[ID_BYTES + PREFIX_BYTES].toInt() and 0xFF,
                )
            }
            else -> null
        }
    }

    /** Returns null if the bytes are not a valid TapShare ID. */
    fun parseId(serviceData: ByteArray?): String? = parseServiceData(serviceData)?.id

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

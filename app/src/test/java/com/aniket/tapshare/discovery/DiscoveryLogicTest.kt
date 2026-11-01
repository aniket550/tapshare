package com.aniket.tapshare.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryProtocolTest {
    @Test
    fun idRoundTrip() {
        val bytes = byteArrayOf(0xA1.toByte(), 0xB2.toByte(), 0x03, 0x04, 0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        assertEquals("A1B20304007F80FF", DiscoveryProtocol.parseId(bytes))
        assertEquals("A1B2·0304", DiscoveryProtocol.shortLabel(DiscoveryProtocol.idToHex(bytes)))
    }

    @Test
    fun parseRejectsWrongSize() {
        assertNull(DiscoveryProtocol.parseId(null))
        assertNull(DiscoveryProtocol.parseId(ByteArray(7)))
        assertNull(DiscoveryProtocol.parseId(ByteArray(9)))
    }

    @Test
    fun payloadsFitInLegacyAdvert() {
        // scan response: service data AD = length(1) + type(1) + 128-bit UUID(16) + ID
        assertTrue(2 + 16 + DiscoveryProtocol.ID_BYTES <= 31)
        // advert: flags(3) + 128-bit UUID list(2 + 16) + TX power(3)
        assertTrue(3 + 18 + 3 <= 31)
    }
}

class RotatingIdTest {
    @Test
    fun rotatesAfterInterval() {
        var now = 0L
        var counter = 0
        val id = RotatingId(now = { now }, randomBytes = { n -> ByteArray(n) { (++counter).toByte() } }, rotationMs = 1000)
        val first = id.current().toList()
        now = 999
        assertEquals(first, id.current().toList())
        assertEquals(1, id.msUntilRotation())
        now = 1000
        val second = id.current().toList()
        assertNotEquals(first, second)
        assertEquals(1000, id.msUntilRotation())
    }

    @Test
    fun currentReturnsCopy() {
        val id = RotatingId(now = { 0 }, randomBytes = { n -> ByteArray(n) { 1 } })
        id.current()[0] = 99
        assertEquals(1.toByte(), id.current()[0])
    }
}

class ScanThrottleTest {
    @Test
    fun allowsFourStartsThenWaitsForWindow() {
        val t = ScanThrottle(maxStarts = 4, windowMs = 30_000)
        repeat(4) { i ->
            assertEquals(0, t.delayBeforeNextStart(i * 1000L))
            t.recordStart(i * 1000L)
        }
        assertEquals(26_000, t.delayBeforeNextStart(4_000)) // oldest start (t=0) leaves the window at 30 s
        assertEquals(0, t.delayBeforeNextStart(30_000))
    }
}

class PeerTableTest {
    @Test
    fun smoothsAndTracksStats() {
        val table = PeerTable(alpha = 0.5, expiryMs = 5_000)
        table.onSample("A", -60, txPower = -7, nowMs = 0)
        table.onSample("A", -40, txPower = null, nowMs = 1_000)
        val p = table.snapshot(1_000).single()
        assertEquals(-40, p.lastRssi)
        assertEquals(-50.0, p.smoothedRssi, 0.001)
        assertEquals(-60, p.minRssi)
        assertEquals(-40, p.maxRssi)
        assertEquals(-50.0, p.avgRssi, 0.001)
        assertEquals(2, p.samples)
        assertEquals(-7, p.txPower) // kept from the first packet
        assertEquals(1.0, p.samplesPerSecond, 0.001)
    }

    @Test
    fun closestFirst_andExpiry() {
        val table = PeerTable(expiryMs = 5_000)
        table.onSample("far", -80, null, 0)
        table.onSample("near", -35, null, 0)
        assertEquals(listOf("near", "far"), table.snapshot(0).map { it.id })
        table.onSample("near", -35, null, 4_000)
        assertEquals(listOf("near"), table.snapshot(6_000).map { it.id }) // "far" not heard for 6 s
        assertTrue(table.prune(6_000))
        assertFalse(table.prune(6_000))
    }

    @Test
    fun resetStatsStartsFromLastValue() {
        val table = PeerTable()
        table.onSample("A", -90, null, 0)
        table.onSample("A", -45, null, 100)
        table.resetStats(200)
        val p = table.snapshot(200).single()
        assertEquals(-45, p.minRssi)
        assertEquals(-45, p.maxRssi)
        assertEquals(1, p.samples)
    }
}

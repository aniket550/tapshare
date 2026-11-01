package com.aniket.tapshare.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapDetectorTest {
    private val cfg = TapConfig(enterLossDb = 30.0, exitMarginDb = 8.0, holdMs = 600, minSamples = 3)

    @Test
    fun pathLossUsesPeerTx() {
        assertEquals(22.0, pathLoss(-43.0, -21), 0.001) // Aniket's phones touching on Ultra low
        assertEquals(59.0, pathLoss(-80.0, null), 0.001) // default TX when missing
    }

    @Test
    fun holdNeedsTimeAndSamples() {
        val d = TapDetector(cfg)
        d.update("B", 22.0, 0)
        d.update("B", 23.0, 300)
        assertFalse(d.isHeld("B", 300))
        assertEquals("B", d.closestTouching())
        d.update("B", 21.0, 650)
        assertTrue(d.isHeld("B", 650))
    }

    @Test
    fun oneFarSampleInHysteresisBandDoesNotReset() {
        val d = TapDetector(cfg)
        d.update("B", 22.0, 0)
        d.update("B", 35.0, 200) // between 30 and 38: keep
        d.update("B", 22.0, 400)
        d.update("B", 22.0, 700)
        assertTrue(d.isHeld("B", 700))
    }

    @Test
    fun pullingApartResetsAndReportsTransition() {
        val d = TapDetector(cfg)
        d.update("B", 22.0, 0)
        assertTrue(d.update("B", 45.0, 100)) // close -> apart
        assertFalse(d.update("B", 50.0, 200)) // already apart
        assertTrue(d.isApart("B"))
        assertNull(d.closestTouching())
    }

    @Test
    fun disarmUntilApart() {
        val d = TapDetector(cfg)
        repeat(4) { d.update("B", 22.0, it * 250L) }
        assertTrue(d.isHeld("B", 750))
        d.disarm("B")
        repeat(4) { d.update("B", 22.0, 1000 + it * 250L) }
        assertFalse(d.isHeld("B", 1750)) // still touching after the session: no second tap
        d.update("B", 50.0, 2000) // pulled apart -> re-armed
        repeat(4) { d.update("B", 22.0, 3000 + it * 250L) }
        assertTrue(d.isHeld("B", 3750))
    }

    @Test
    fun disarmedPhoneIsNotForgottenWhileRadiosArePaused() {
        val d = TapDetector(cfg)
        repeat(4) { d.update("B", 22.0, it * 250L) }
        d.disarm("B")
        d.forgetOlderThan(nowMs = 60_000, maxAgeMs = 10_000) // long share session, no packets
        repeat(4) { d.update("B", 22.0, 60_000 + it * 250L) } // still touching afterwards
        assertFalse(d.isHeld("B", 60_750))
    }

    @Test
    fun closestTouchingPicksLowestLoss() {
        val d = TapDetector(cfg)
        d.update("far-ish", 29.0, 0)
        d.update("near", 18.0, 0)
        assertEquals("near", d.closestTouching())
    }

    @Test
    fun calibratorUsesMedianPlusMarginAndClamps() {
        val c = TapCalibrator(marginDb = 8.0)
        listOf(20.0, 21.0, 22.0, 23.0, 60.0, 22.0, 21.0, 22.0).forEach(c::add)
        assertEquals(30.0, c.result()!!, 0.001) // median 22 + 8; the 60 outlier is ignored
        assertNull(TapCalibrator().also { it.add(20.0) }.result())
        val loud = TapCalibrator(marginDb = 8.0).also { r -> repeat(10) { r.add(5.0) } }
        assertEquals(TapCalibrator.MIN_ENTER_DB, loud.result()!!, 0.001)
    }

    @Test
    fun serviceDataRoundTrip() {
        val id = DiscoveryProtocol.hexToBytes("A1B2C3D4E5F60718")
        val data = DiscoveryProtocol.encodeServiceData(id, "11223344AABBCCDD", DiscoveryProtocol.FLAG_TRIGGERED)
        assertEquals(13, data.size)
        assertTrue(2 + 16 + data.size <= 31) // fits the scan response
        val parsed = DiscoveryProtocol.parseServiceData(data)!!
        assertEquals("A1B2C3D4E5F60718", parsed.id)
        assertEquals("11223344", parsed.closeToPrefix)
        assertEquals(DiscoveryProtocol.FLAG_TRIGGERED, parsed.flags)
        val nobody = DiscoveryProtocol.parseServiceData(DiscoveryProtocol.encodeServiceData(id, null, 0))!!
        assertNull(nobody.closeToPrefix)
        assertEquals("11223344", DiscoveryProtocol.prefixOf("11223344AABBCCDD"))
    }
}

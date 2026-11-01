package com.aniket.tapshare.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessPolicyTest {

    private val allGood = DeviceConditions(
        sharingEnabled = true,
        permissionsGranted = true,
        bluetooth = BluetoothStatus.ON,
        locationServicesNeeded = false,
        locationServicesOn = false,
        screen = ScreenState(interactive = true, locked = false),
    )

    private fun eval(c: DeviceConditions) = ReadinessPolicy.evaluate(c)

    @Test
    fun ready_whenEverythingIsFine() {
        assertEquals(Readiness.Ready, eval(allGood))
    }

    @Test
    fun screenOff_and_locked_arePauses() {
        assertEquals(
            Readiness.Paused(PauseReason.SCREEN_OFF),
            eval(allGood.copy(screen = ScreenState(interactive = false, locked = true))),
        )
        assertEquals(
            Readiness.Paused(PauseReason.LOCKED),
            eval(allGood.copy(screen = ScreenState(interactive = true, locked = true))),
        )
    }

    @Test
    fun bluetoothNotOn_isBlocked_includingTransitions() {
        for (s in listOf(BluetoothStatus.OFF, BluetoothStatus.TURNING_ON, BluetoothStatus.TURNING_OFF)) {
            assertEquals(Readiness.Blocked(Problem.BLUETOOTH_OFF), eval(allGood.copy(bluetooth = s)))
        }
    }

    @Test
    fun locationOff_onlyMattersWhereNeeded() {
        assertEquals(
            Readiness.Blocked(Problem.LOCATION_SERVICES_OFF),
            eval(allGood.copy(locationServicesNeeded = true, locationServicesOn = false)),
        )
        assertEquals(Readiness.Ready, eval(allGood.copy(locationServicesNeeded = true, locationServicesOn = true)))
    }

    @Test
    fun problemsWinOverPauses_inPriorityOrder() {
        val everythingWrong = DeviceConditions(
            sharingEnabled = false,
            permissionsGranted = false,
            bluetooth = BluetoothStatus.UNSUPPORTED,
            locationServicesNeeded = true,
            locationServicesOn = false,
            screen = ScreenState(interactive = false, locked = true),
        )
        assertEquals(Readiness.Blocked(Problem.BLUETOOTH_UNSUPPORTED), eval(everythingWrong))
        val supported = everythingWrong.copy(bluetooth = BluetoothStatus.OFF)
        assertEquals(Readiness.Blocked(Problem.SHARING_OFF), eval(supported))
        assertEquals(Readiness.Blocked(Problem.MISSING_PERMISSIONS), eval(supported.copy(sharingEnabled = true)))
        assertEquals(
            Readiness.Blocked(Problem.BLUETOOTH_OFF),
            eval(supported.copy(sharingEnabled = true, permissionsGranted = true)),
        )
    }

    @Test
    fun serviceKeepsRunningThroughBluetoothOff_butNotWhenSharingOff() {
        assertTrue(ReadinessPolicy.serviceShouldRun(true, true, BluetoothStatus.OFF))
        assertFalse(ReadinessPolicy.serviceShouldRun(false, true, BluetoothStatus.ON))
        assertFalse(ReadinessPolicy.serviceShouldRun(true, false, BluetoothStatus.ON))
        assertFalse(ReadinessPolicy.serviceShouldRun(true, true, BluetoothStatus.UNSUPPORTED))
    }
}

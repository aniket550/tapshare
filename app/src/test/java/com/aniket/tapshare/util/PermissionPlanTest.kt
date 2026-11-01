package com.aniket.tapshare.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPlanTest {

    private fun names(sdk: Int) = PermissionPlan.permissionsFor(sdk).map { it.name }.toSet()

    @Test
    fun android10_asksOnlyForLocation() {
        assertEquals(setOf("FINE_LOCATION", "COARSE_LOCATION"), names(29))
        assertEquals(listOf(PermissionGroup.LOCATION), PermissionPlan.groupsFor(29))
    }

    @Test
    fun android12_asksBluetoothAndLocation() {
        assertEquals(
            setOf("FINE_LOCATION", "COARSE_LOCATION", "BLUETOOTH_SCAN", "BLUETOOTH_ADVERTISE", "BLUETOOTH_CONNECT"),
            names(31),
        )
        assertEquals(names(31), names(32))
    }

    @Test
    fun android13_dropsLocation_addsWifiAndNotifications() {
        assertEquals(
            setOf("BLUETOOTH_SCAN", "BLUETOOTH_ADVERTISE", "BLUETOOTH_CONNECT", "NEARBY_WIFI_DEVICES", "POST_NOTIFICATIONS"),
            names(33),
        )
        assertEquals(
            listOf(PermissionGroup.NEARBY_DEVICES, PermissionGroup.NOTIFICATIONS),
            PermissionPlan.groupsFor(33),
        )
    }

    @Test
    fun android17_addsLocalNetwork() {
        assertTrue("ACCESS_LOCAL_NETWORK" in names(37))
        assertFalse("ACCESS_LOCAL_NETWORK" in names(36))
    }

    @Test
    fun grantState_rules() {
        assertEquals(GrantState.GRANTED, PermissionPlan.grantState(granted = true, askedBefore = true, shouldShowRationale = false))
        assertEquals(GrantState.CAN_REQUEST, PermissionPlan.grantState(granted = false, askedBefore = false, shouldShowRationale = false))
        assertEquals(GrantState.CAN_REQUEST, PermissionPlan.grantState(granted = false, askedBefore = true, shouldShowRationale = true))
        assertEquals(GrantState.BLOCKED, PermissionPlan.grantState(granted = false, askedBefore = true, shouldShowRationale = false))
    }

    @Test
    fun summary_optionalNotificationsDoNotBlockTheApp() {
        val summary = PermissionPlan.summarize(
            sdk = 34,
            isGranted = { it != AppPermission.POST_NOTIFICATIONS },
            askedBefore = { true },
            shouldShowRationale = { false },
        )
        assertTrue(summary.allRequiredGranted)
        assertEquals(GrantState.BLOCKED, summary.stateOf(PermissionGroup.NOTIFICATIONS))
        assertFalse(summary.anyRequiredBlocked)
    }

    @Test
    fun summary_groupTakesWorstStateAndListsOnlyMissing() {
        val summary = PermissionPlan.summarize(
            sdk = 33,
            isGranted = { it == AppPermission.BLUETOOTH_SCAN },
            askedBefore = { it == AppPermission.BLUETOOTH_CONNECT },
            shouldShowRationale = { false },
        )
        val nearby = summary.groups.first { it.group == PermissionGroup.NEARBY_DEVICES }
        assertEquals(GrantState.BLOCKED, nearby.state)
        assertFalse(AppPermission.BLUETOOTH_SCAN in nearby.missing)
        assertTrue(AppPermission.BLUETOOTH_CONNECT in nearby.missing)
        assertFalse(summary.allRequiredGranted)
        assertTrue(summary.anyRequiredBlocked)
        // Blocked groups are not offered as a system dialog.
        assertTrue(summary.requestable.none { it.group == PermissionGroup.NEARBY_DEVICES })
        assertEquals(listOf(AppPermission.POST_NOTIFICATIONS), summary.requestable)
    }

    @Test
    fun summary_firstLaunchEverythingRequestable() {
        val summary = PermissionPlan.summarize(31, { false }, { false }, { false })
        assertEquals(PermissionPlan.permissionsFor(31).toSet(), summary.requestable.toSet())
    }
}

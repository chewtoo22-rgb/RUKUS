package com.ruckus.agent.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCapabilitySnapshotTest {
    @Test
    fun shellRemainsBlockedWhenAdapterIsDisabled() {
        val state = RuntimeCapabilitySnapshot(
            accessibilityReady = true,
            writeSettingsReady = true,
            shizukuBinderAvailable = true,
            shizukuPermissionGranted = true,
            approvedShellAdapterEnabled = false
        )

        assertFalse(state.approvedShellReady)
    }

    @Test
    fun shellRemainsBlockedWithoutBinder() {
        val state = RuntimeCapabilitySnapshot(
            accessibilityReady = true,
            writeSettingsReady = true,
            shizukuBinderAvailable = false,
            shizukuPermissionGranted = true,
            approvedShellAdapterEnabled = true
        )

        assertFalse(state.approvedShellReady)
    }

    @Test
    fun shellRemainsBlockedWithoutPermission() {
        val state = RuntimeCapabilitySnapshot(
            accessibilityReady = true,
            writeSettingsReady = true,
            shizukuBinderAvailable = true,
            shizukuPermissionGranted = false,
            approvedShellAdapterEnabled = true
        )

        assertFalse(state.approvedShellReady)
    }

    @Test
    fun shellBecomesReadyOnlyWhenEveryBoundaryIsReady() {
        val state = RuntimeCapabilitySnapshot(
            accessibilityReady = true,
            writeSettingsReady = true,
            shizukuBinderAvailable = true,
            shizukuPermissionGranted = true,
            approvedShellAdapterEnabled = true
        )

        assertTrue(state.approvedShellReady)
    }
}

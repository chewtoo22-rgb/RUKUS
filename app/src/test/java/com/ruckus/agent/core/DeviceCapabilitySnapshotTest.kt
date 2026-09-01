package com.ruckus.agent.core

import com.ruckus.agent.control.ShizukuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitySnapshotTest {
    @Test
    fun basicControlOnlyDependsOnAccessibility() {
        val snapshot = DeviceCapabilityReader.fromStates(
            accessibilityReady = true,
            writeSettingsReady = false,
            shizukuBinderAvailable = false,
            shizukuPermissionGranted = false,
            approvedShellReady = false
        )

        assertTrue(snapshot.basicControlReady)
        assertFalse(snapshot.brightnessControlReady)
        assertFalse(snapshot.privilegedControlReady)
        assertTrue(snapshot.missing().any { it.capability == DeviceCapability.WRITE_SETTINGS })
        assertTrue(snapshot.missing().any { it.capability == DeviceCapability.SHIZUKU_BINDER })
    }

    @Test
    fun shizukuPermissionCannotBeReadyWithoutBinder() {
        val snapshot = DeviceCapabilityReader.fromStates(
            accessibilityReady = true,
            writeSettingsReady = true,
            shizukuBinderAvailable = false,
            shizukuPermissionGranted = true,
            approvedShellReady = true
        )

        assertFalse(snapshot.isReady(DeviceCapability.SHIZUKU_PERMISSION))
        assertFalse(snapshot.isReady(DeviceCapability.APPROVED_SHELL))
        assertFalse(snapshot.privilegedControlReady)
    }

    @Test
    fun privilegedControlRequiresAccessibilityShizukuAndAdapter() {
        val snapshot = DeviceCapabilityReader.fromStates(
            accessibilityReady = true,
            writeSettingsReady = true,
            shizukuBinderAvailable = true,
            shizukuPermissionGranted = true,
            approvedShellReady = true
        )

        assertTrue(snapshot.basicControlReady)
        assertTrue(snapshot.brightnessControlReady)
        assertTrue(snapshot.privilegedControlReady)
        assertTrue(snapshot.missing().isEmpty())
    }

    @Test
    fun missingCapabilitiesCarryActionableRemediation() {
        val snapshot = DeviceCapabilityReader.fromStates(
            accessibilityReady = false,
            writeSettingsReady = false,
            shizukuBinderAvailable = true,
            shizukuPermissionGranted = false,
            approvedShellReady = false
        )

        assertTrue(snapshot.missing().all { it.remediation.isNotBlank() })
        assertTrue(
            snapshot.missing()
                .first { it.capability == DeviceCapability.SHIZUKU_PERMISSION }
                .remediation.contains("Grant RUKUS permission")
        )
    }

    @Test
    fun writeSettingsSamplingFailureFailsClosed() {
        assertFalse(
            DeviceCapabilityReader.safeWriteSettingsRead {
                throw SecurityException("settings provider unavailable")
            }
        )
    }

    @Test
    fun writeSettingsSamplingPreservesGrantedAndDeniedStates() {
        assertTrue(DeviceCapabilityReader.safeWriteSettingsRead { true })
        assertFalse(DeviceCapabilityReader.safeWriteSettingsRead { false })
    }

    @Test(expected = AssertionError::class)
    fun writeSettingsSamplingDoesNotHideFatalErrors() {
        DeviceCapabilityReader.safeWriteSettingsRead {
            throw AssertionError("fatal settings failure")
        }
    }

    @Test
    fun shizukuSamplingFailureFailsClosed() {
        assertEquals(
            null,
            DeviceCapabilityReader.safeShizukuStateRead {
                throw SecurityException("binder permission query unavailable")
            }
        )
    }

    @Test
    fun shizukuSamplingPreservesValidState() {
        val state = ShizukuState(binderAvailable = true, permissionGranted = false)
        assertEquals(state, DeviceCapabilityReader.safeShizukuStateRead { state })
    }

    @Test(expected = AssertionError::class)
    fun shizukuSamplingDoesNotHideFatalErrors() {
        DeviceCapabilityReader.safeShizukuStateRead {
            throw AssertionError("fatal binder failure")
        }
    }
}

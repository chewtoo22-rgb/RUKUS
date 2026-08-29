package com.ruckus.agent.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRemediationPlannerTest {
    @Test
    fun `ready non-shell capabilities leave only internal adapter step`() {
        val steps = CapabilityRemediationPlanner.plan(
            RuntimeCapabilitySnapshot(
                accessibilityReady = true,
                writeSettingsReady = true,
                shizukuBinderAvailable = true,
                shizukuPermissionGranted = true,
                approvedShellAdapterEnabled = false
            )
        )

        assertEquals(1, steps.size)
        assertEquals(CapabilityRemediationKind.ENABLE_APPROVED_SHELL_ADAPTER, steps.single().kind)
        assertFalse(steps.single().userActionable)
    }

    @Test
    fun `missing capabilities are returned in deterministic recovery order`() {
        val steps = CapabilityRemediationPlanner.plan(
            RuntimeCapabilitySnapshot(
                accessibilityReady = false,
                writeSettingsReady = false,
                shizukuBinderAvailable = false,
                shizukuPermissionGranted = false,
                approvedShellAdapterEnabled = false
            )
        )

        assertEquals(
            listOf(
                CapabilityRemediationKind.ENABLE_ACCESSIBILITY,
                CapabilityRemediationKind.GRANT_WRITE_SETTINGS,
                CapabilityRemediationKind.START_SHIZUKU,
                CapabilityRemediationKind.ENABLE_APPROVED_SHELL_ADAPTER
            ),
            steps.map { it.kind }
        )
        assertTrue(steps.take(3).all { it.userActionable })
        assertFalse(steps.last().userActionable)
    }

    @Test
    fun `Shizuku permission is requested only after binder is available`() {
        val steps = CapabilityRemediationPlanner.plan(
            RuntimeCapabilitySnapshot(
                accessibilityReady = true,
                writeSettingsReady = true,
                shizukuBinderAvailable = true,
                shizukuPermissionGranted = false,
                approvedShellAdapterEnabled = true
            )
        )

        assertEquals(
            listOf(CapabilityRemediationKind.GRANT_SHIZUKU_PERMISSION),
            steps.map { it.kind }
        )
    }

    @Test
    fun `Shizuku planner rejects invalid request code`() {
        assertEquals(
            ShizukuPermissionDecision.INVALID_REQUEST_CODE,
            ShizukuPermissionPlanner.decide(
                ShizukuState(binderAvailable = true, permissionGranted = false),
                -1
            )
        )
    }

    @Test
    fun `Shizuku planner distinguishes unavailable granted and requestable states`() {
        assertEquals(
            ShizukuPermissionDecision.SERVICE_UNAVAILABLE,
            ShizukuPermissionPlanner.decide(
                ShizukuState(binderAvailable = false, permissionGranted = false),
                42
            )
        )
        assertEquals(
            ShizukuPermissionDecision.ALREADY_GRANTED,
            ShizukuPermissionPlanner.decide(
                ShizukuState(binderAvailable = true, permissionGranted = true),
                42
            )
        )
        assertEquals(
            ShizukuPermissionDecision.REQUEST_PERMISSION,
            ShizukuPermissionPlanner.decide(
                ShizukuState(binderAvailable = true, permissionGranted = false),
                42
            )
        )
    }
}

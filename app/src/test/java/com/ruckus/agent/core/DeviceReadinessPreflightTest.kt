package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class DeviceReadinessPreflightTest {
    @Test fun accessibilityOfflineBlocksBeforeAnyRemainingAction() {
        val actions = listOf(AgentAction.SetMediaVolume(20), AgentAction.Home)
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = 0,
            accessibilityReady = false,
            writeSettingsReady = true
        )
        assertFalse(decision.allowed)
        assertEquals(0, decision.actionIndex)
        assertEquals(actions[0], decision.action)
        assertTrue(decision.reason.contains("Accessibility"))
    }

    @Test fun laterBrightnessPermissionBlocksWholeRemainingPlan() {
        val actions = listOf(AgentAction.SetMediaVolume(20), AgentAction.SetBrightness(40))
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = 0,
            accessibilityReady = true,
            writeSettingsReady = false
        )
        assertFalse(decision.allowed)
        assertEquals(1, decision.actionIndex)
        assertEquals(actions[1], decision.action)
        assertTrue(decision.reason.contains("WRITE_SETTINGS"))
    }

    @Test fun unavailableShellAdapterBlocksEarlierSideEffects() {
        val shell = AgentAction.RunApprovedShell("wifi-status")
        val actions = listOf(AgentAction.SetMediaVolume(20), shell)
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = 0,
            accessibilityReady = true,
            writeSettingsReady = true,
            approvedShellReady = false
        )
        assertFalse(decision.allowed)
        assertEquals(1, decision.actionIndex)
        assertEquals(shell, decision.action)
        assertTrue(decision.reason.contains("shell", ignoreCase = true))
    }

    @Test fun completedShellStepDoesNotPoisonResumedRemainder() {
        val actions = listOf(AgentAction.RunApprovedShell("wifi-status"), AgentAction.Home)
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = 1,
            accessibilityReady = true,
            writeSettingsReady = true,
            approvedShellReady = false
        )
        assertTrue(decision.allowed)
    }

    @Test fun shellStepIsAdmittedOnlyWhenAdapterIsReady() {
        val decision = DeviceReadinessPreflight.evaluate(
            actions = listOf(AgentAction.RunApprovedShell("wifi-status")),
            startStep = 0,
            accessibilityReady = true,
            writeSettingsReady = true,
            approvedShellReady = true
        )
        assertTrue(decision.allowed)
    }

    @Test fun completedBrightnessDoesNotPoisonResumedRemainder() {
        val actions = listOf(AgentAction.SetBrightness(40), AgentAction.Home)
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = 1,
            accessibilityReady = true,
            writeSettingsReady = false
        )
        assertTrue(decision.allowed)
    }

    @Test fun fullyReadyPlanIsAdmitted() {
        val decision = DeviceReadinessPreflight.evaluate(
            actions = listOf(AgentAction.Home, AgentAction.SetBrightness(40)),
            startStep = 0,
            accessibilityReady = true,
            writeSettingsReady = true
        )
        assertTrue(decision.allowed)
    }

    @Test fun emptyRemainderNeedsNoDeviceCapability() {
        val actions = listOf(AgentAction.Home)
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = actions.size,
            accessibilityReady = false,
            writeSettingsReady = false
        )
        assertTrue(decision.allowed)
    }

    @Test fun invalidStartStepFailsClosed() {
        val actions = listOf(AgentAction.Home)
        assertFalse(DeviceReadinessPreflight.evaluate(actions, -1, true, true).allowed)
        assertFalse(DeviceReadinessPreflight.evaluate(actions, 2, true, true).allowed)
    }
}

package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceReadinessPreflightDeviceTest {
    @Test fun observationSourceMustBeReadyBeforeAnyActionIsAdmitted() {
        val actions = listOf(AgentAction.OpenApp("com.example.app"), AgentAction.Home)
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = 0,
            accessibilityReady = false,
            writeSettingsReady = true
        )
        assertFalse(decision.allowed)
        assertEquals(0, decision.actionIndex)
    }

    @Test fun futurePermissionGatedStepBlocksEarlierSideEffects() {
        val actions = listOf(AgentAction.SetMediaVolume(25), AgentAction.SetBrightness(60))
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = 0,
            accessibilityReady = true,
            writeSettingsReady = false
        )
        assertFalse(decision.allowed)
        assertEquals(1, decision.actionIndex)
        assertEquals(AgentAction.SetBrightness(60), decision.action)
    }

    @Test fun unavailableShellAdapterBlocksWholeRemainingPlan() {
        val shell = AgentAction.RunApprovedShell("wifi-status")
        val actions = listOf(AgentAction.Home, shell)
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
    }

    @Test fun resumedSuffixIgnoresAlreadyCompletedShellCapability() {
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

    @Test fun unavailableFutureAppBlocksWholeRemainingPlan() {
        val missing = AgentAction.OpenAppByName("Definitely Missing")
        val actions = listOf(AgentAction.SetMediaVolume(25), missing)
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = 0,
            accessibilityReady = true,
            writeSettingsReady = true,
            approvedShellReady = true,
            launchTargetReady = { it != missing }
        )
        assertFalse(decision.allowed)
        assertEquals(1, decision.actionIndex)
        assertEquals(missing, decision.action)
    }

    @Test fun ambiguousVisibleAppNameFailsClosedBeforeDispatch() {
        val candidates = listOf(
            AppLaunchMatchPolicy.Candidate("com.example.photos", "Photos"),
            AppLaunchMatchPolicy.Candidate("com.example.photoeditor", "Photo Editor")
        )
        assertNull(AppLaunchMatchPolicy.resolve("Photo", candidates))

        val launch = AgentAction.OpenAppByName("Photo")
        val decision = DeviceReadinessPreflight.evaluate(
            actions = listOf(AgentAction.SetMediaVolume(25), launch),
            startStep = 0,
            accessibilityReady = true,
            writeSettingsReady = true,
            approvedShellReady = true,
            launchTargetReady = { action ->
                action !is AgentAction.OpenAppByName || AppLaunchMatchPolicy.resolve(action.appName, candidates) != null
            }
        )
        assertFalse(decision.allowed)
        assertEquals(1, decision.actionIndex)
        assertEquals(launch, decision.action)
    }
}

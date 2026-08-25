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
}

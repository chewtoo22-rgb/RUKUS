package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompletionRepairPolicyDeviceTest {
    @Test
    fun textCompletionRepairFailsClosedRatherThanDuplicatingInput() {
        val original = AgentAction.TypeText("hello")

        val repair = TaskCompletionRepairPlanner.plan(
            original,
            "final text evidence unavailable"
        )

        assertNull(repair.action)
        assertTrue(repair.reason.contains("do not replay", ignoreCase = true))
    }

    @Test
    fun idempotentForegroundRepairStillReusesExactRequestedPackage() {
        val original = AgentAction.OpenApp("com.spotify.music")

        val repair = TaskCompletionRepairPlanner.plan(
            original,
            "foreground drifted"
        )

        assertEquals(original, repair.action)
        assertEquals(Risk.SAFE, SafetyGate.classify(repair.action!!).risk)
    }
}

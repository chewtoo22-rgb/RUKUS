package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashRecoveryPolicyDeviceTest {
    @Test
    fun ambiguousTextEntryNeverReplaysAfterCrash() {
        val textDecision = CrashRecoveryPolicy.decide(AgentAction.TypeText("hello"))
        val volumeDecision = CrashRecoveryPolicy.decide(AgentAction.SetMediaVolume(25))

        assertFalse(textDecision.replayAllowed)
        assertTrue(textDecision.reason.contains("duplicate", ignoreCase = true))
        assertTrue(volumeDecision.replayAllowed)
    }
}

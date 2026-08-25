package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashRecoveryPolicyDeviceTest {
    @Test
    fun observedTextEffectCanBeReconciledWithoutReplay() {
        val action = AgentAction.TypeText("hello")
        val before = "pkg=com.example.notes | text=Draft"
        val after = "pkg=com.example.notes | text=Draft hello"

        val verification = ActionVerifier.verify(action, before, after, null)
        val replay = CrashRecoveryPolicy.decide(action)

        assertTrue(verification.ok)
        assertFalse(replay.replayAllowed)
    }

    @Test
    fun ambiguousTextEntryNeverReplaysAfterCrash() {
        val action = AgentAction.TypeText("hello")
        val before = "pkg=com.example.notes | text=Draft"
        val after = "pkg=com.example.notes | text=Draft"

        val verification = ActionVerifier.verify(action, before, after, null)
        val textDecision = CrashRecoveryPolicy.decide(action)
        val volumeDecision = CrashRecoveryPolicy.decide(AgentAction.SetMediaVolume(25))

        assertFalse(verification.ok)
        assertFalse(textDecision.replayAllowed)
        assertTrue(textDecision.reason.contains("duplicate", ignoreCase = true))
        assertTrue(volumeDecision.replayAllowed)
    }

    @Test
    fun privilegedActionRemainsNonReplayableWhenOutcomeIsUnproven() {
        val action = AgentAction.RunApprovedShell("settings put secure example 1")

        val verification = ActionVerifier.verify(action, null, null, null)
        val replay = CrashRecoveryPolicy.decide(action)

        assertFalse(verification.ok)
        assertFalse(replay.replayAllowed)
        assertTrue(replay.reason.contains("never replayed", ignoreCase = true))
    }
}

package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReplayAdmissionPolicyTest {

    @Test
    fun replaySafeCrashConsumesRecoveryBudget() {
        val decision = CrashReplayAdmissionPolicy.decide(
            AgentAction.SetMediaVolume(40),
            recoveryAttempts = 1
        )

        assertTrue(decision.replayAllowed)
        assertEquals(2, decision.nextRecoveryAttempts)
        assertTrue(decision.reason.contains("2/3"))
    }

    @Test
    fun exhaustedBudgetBlocksOtherwiseReplaySafeCrash() {
        val decision = CrashReplayAdmissionPolicy.decide(
            AgentAction.Home,
            recoveryAttempts = RecoveryBudget.MAX_TOTAL_ATTEMPTS
        )

        assertFalse(decision.replayAllowed)
        assertEquals(RecoveryBudget.MAX_TOTAL_ATTEMPTS, decision.nextRecoveryAttempts)
        assertTrue(decision.reason.contains("exhausted", ignoreCase = true))
    }

    @Test
    fun nonReplayableActionNeverConsumesBudget() {
        val decision = CrashReplayAdmissionPolicy.decide(
            AgentAction.TypeText("hello"),
            recoveryAttempts = 1
        )

        assertFalse(decision.replayAllowed)
        assertEquals(1, decision.nextRecoveryAttempts)
        assertTrue(decision.reason.contains("ambiguous", ignoreCase = true))
    }

    @Test
    fun negativeRecoveryCountFailsClosed() {
        val decision = CrashReplayAdmissionPolicy.decide(
            AgentAction.Home,
            recoveryAttempts = -1
        )

        assertFalse(decision.replayAllowed)
        assertEquals(-1, decision.nextRecoveryAttempts)
        assertTrue(decision.reason.contains("invalid", ignoreCase = true))
    }
}

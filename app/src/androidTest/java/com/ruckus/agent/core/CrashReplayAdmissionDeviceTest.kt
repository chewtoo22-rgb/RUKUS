package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android-runtime probes for task-wide crash replay budgeting. */
class CrashReplayAdmissionDeviceTest {

    @Test
    fun repeatedCrashReplayCannotBypassTaskRecoveryCap() {
        val first = CrashReplayAdmissionPolicy.decide(AgentAction.Home, 1)
        assertTrue(first.replayAllowed)
        assertEquals(2, first.nextRecoveryAttempts)

        val second = CrashReplayAdmissionPolicy.decide(AgentAction.Home, first.nextRecoveryAttempts)
        assertTrue(second.replayAllowed)
        assertEquals(3, second.nextRecoveryAttempts)

        val exhausted = CrashReplayAdmissionPolicy.decide(AgentAction.Home, second.nextRecoveryAttempts)
        assertFalse(exhausted.replayAllowed)
        assertEquals(RecoveryBudget.MAX_TOTAL_ATTEMPTS, exhausted.nextRecoveryAttempts)
    }

    @Test
    fun ambiguousTextEntryRemainsNonReplayableWithoutSpendingBudget() {
        val decision = CrashReplayAdmissionPolicy.decide(
            AgentAction.TypeText("device probe"),
            recoveryAttempts = 2
        )

        assertFalse(decision.replayAllowed)
        assertEquals(2, decision.nextRecoveryAttempts)
    }
}

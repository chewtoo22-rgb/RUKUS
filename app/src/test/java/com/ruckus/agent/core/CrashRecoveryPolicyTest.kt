package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class CrashRecoveryPolicyTest {
    @Test fun boundedIdempotentActionsMayReplay() {
        assertTrue(CrashRecoveryPolicy.decide(AgentAction.OpenApp("com.spotify.music")).replayAllowed)
        assertTrue(CrashRecoveryPolicy.decide(AgentAction.TypeText("hello")).replayAllowed)
        assertTrue(CrashRecoveryPolicy.decide(AgentAction.SetMediaVolume(25)).replayAllowed)
        assertTrue(CrashRecoveryPolicy.decide(AgentAction.Home).replayAllowed)
    }

    @Test fun ambiguousGesturesAndPrivilegedActionsNeverReplay() {
        assertFalse(CrashRecoveryPolicy.decide(AgentAction.Back).replayAllowed)
        assertFalse(CrashRecoveryPolicy.decide(AgentAction.TapLabel("Allow")).replayAllowed)
        assertFalse(CrashRecoveryPolicy.decide(AgentAction.Scroll(AgentAction.Direction.DOWN)).replayAllowed)
        assertFalse(CrashRecoveryPolicy.decide(AgentAction.RunApprovedShell("demo")).replayAllowed)
    }

    @Test fun executingCheckpointIsResumableOnlyWithExactPlan() {
        val plan=CommandPlanner.plan("open Spotify then volume 25")
        val session=PersistedTaskSession(
            request="open Spotify then volume 25",
            currentStep=0,
            totalSteps=2,
            lastAction="OpenAppByName(appName=Spotify)",
            lastScreenSummary="pkg=launcher",
            recoveryAttempts=0,
            status=AgentTaskState.Status.EXECUTING,
            savedAtMs=1L,
            planFingerprint=PlanFingerprint.of(plan)
        )
        val decision=ResumePolicy.decide(session,plan)
        assertTrue(decision.allowed)
        assertEquals(0,decision.startStep)
        assertTrue(decision.reason.contains("in-flight",ignoreCase=true))
    }
}

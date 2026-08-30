package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android-runtime probes for crash checkpoints created by bounded completion repair.
 *
 * Completion repair may replay only the exact safe terminal action. A crash before that
 * repair dispatch must therefore resume from the terminal plan index, never from the
 * already-completed plan boundary.
 */
class CompletionRepairResumeDeviceTest {

    @Test
    fun normalizedCompletionRepairCheckpointResumesAtTerminalAction() {
        val terminal = AgentAction.OpenApp("com.spotify.music")
        val plan = CommandPlanner.Plan(listOf(AgentAction.Home, terminal), emptyList())
        val repair = TaskCompletionRepairPlanner.plan(terminal, "foreground goal not proven")
        val session = checkpoint(
            plan = plan,
            currentStep = plan.actions.lastIndex,
            lastAction = repair.action
        )

        val decision = ResumePolicy.decide(session, plan)

        assertEquals(terminal, repair.action)
        assertTrue(decision.allowed)
        assertEquals(plan.actions.lastIndex, decision.startStep)
    }

    @Test
    fun completedPlanBoundaryCannotMasqueradeAsPendingCompletionRepair() {
        val terminal = AgentAction.OpenApp("com.spotify.music")
        val plan = CommandPlanner.Plan(listOf(AgentAction.Home, terminal), emptyList())
        val session = checkpoint(
            plan = plan,
            currentStep = plan.actions.size,
            lastAction = terminal
        )

        val decision = ResumePolicy.decide(session, plan)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("checkpointed", ignoreCase = true))
    }

    private fun checkpoint(
        plan: CommandPlanner.Plan,
        currentStep: Int,
        lastAction: AgentAction?
    ): PersistedTaskSession = PersistedTaskSession(
        request = "device completion repair resume probe",
        currentStep = currentStep,
        totalSteps = plan.actions.size,
        lastAction = lastAction?.toString(),
        lastScreenSummary = "pkg=com.spotify.music | labels=Spotify",
        recoveryAttempts = 1,
        status = AgentTaskState.Status.RECOVERING,
        savedAtMs = System.currentTimeMillis(),
        planFingerprint = PlanFingerprint.of(plan)
    )
}

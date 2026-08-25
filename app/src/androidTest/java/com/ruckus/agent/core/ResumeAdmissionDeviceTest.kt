package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android-runtime probes for the resume admission firewall.
 *
 * These tests intentionally stop before DeviceController dispatch. They prove that a crash
 * checkpoint cannot become execution authority unless the regenerated plan is semantically
 * identical and the remaining whole plan still satisfies the exact-action safety preflight.
 */
class ResumeAdmissionDeviceTest {

    @Test
    fun resumeRejectsSameShapePlanWhenSemanticsChanged() {
        val savedPlan = CommandPlanner.plan("open Spotify then volume 35 then scroll down")
        val regenerated = CommandPlanner.plan("open Spotify then volume 80 then scroll down")
        val session = checkpoint(
            plan = savedPlan,
            currentStep = 1,
            status = AgentTaskState.Status.RUNNING,
            lastAction = savedPlan.actions[0]
        )

        val decision = ResumePolicy.decide(session, regenerated)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("semantics changed", ignoreCase = true))
    }

    @Test
    fun executingCheckpointRejectsDifferentInFlightActionIdentity() {
        val plan = CommandPlanner.plan("open Spotify then volume 35")
        val session = checkpoint(
            plan = plan,
            currentStep = 1,
            status = AgentTaskState.Status.EXECUTING,
            lastAction = AgentAction.SetMediaVolume(80)
        )

        val decision = ResumePolicy.decide(session, plan)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("differs from the exact saved plan", ignoreCase = true))
    }

    @Test
    fun resumedRemainingPlanStillRequiresExactPrivilegedConfirmation() {
        val privileged = AgentAction.RunApprovedShell("demo")
        val actions = listOf(AgentAction.Home, privileged)
        val plan = CommandPlanner.Plan(actions, emptyList())
        val session = checkpoint(
            plan = plan,
            currentStep = 1,
            status = AgentTaskState.Status.WAITING_CONFIRMATION,
            lastAction = privileged
        )

        val resume = ResumePolicy.decide(session, plan)
        assertTrue(resume.allowed)
        assertEquals(1, resume.startStep)

        val withoutApproval = PlanSafetyPreflight.evaluate(
            actions = actions,
            startStep = resume.startStep,
            approved = false,
            approvedActionFingerprint = null
        )
        assertFalse(withoutApproval.allowed)
        assertTrue(withoutApproval.needsConfirmation)
        assertEquals(privileged, withoutApproval.action)

        val wrongApproval = PlanSafetyPreflight.evaluate(
            actions = actions,
            startStep = resume.startStep,
            approved = true,
            approvedActionFingerprint = PlanSafetyPreflight.approvalFingerprint(AgentAction.RunApprovedShell("different"))
        )
        assertFalse(wrongApproval.allowed)
        assertTrue(wrongApproval.needsConfirmation)

        val exactApproval = PlanSafetyPreflight.evaluate(
            actions = actions,
            startStep = resume.startStep,
            approved = true,
            approvedActionFingerprint = PlanSafetyPreflight.approvalFingerprint(privileged)
        )
        assertTrue(exactApproval.allowed)
    }

    @Test
    fun exactPlanResumeStartsAtFirstUnverifiedStep() {
        val plan = CommandPlanner.plan("open Spotify then volume 35 then scroll down")
        val session = checkpoint(
            plan = plan,
            currentStep = 2,
            status = AgentTaskState.Status.RUNNING,
            lastAction = plan.actions[1]
        )

        val decision = ResumePolicy.decide(session, plan)

        assertTrue(decision.allowed)
        assertEquals(2, decision.startStep)
    }

    private fun checkpoint(
        plan: CommandPlanner.Plan,
        currentStep: Int,
        status: AgentTaskState.Status,
        lastAction: AgentAction?
    ): PersistedTaskSession = PersistedTaskSession(
        request = "device resume probe",
        currentStep = currentStep,
        totalSteps = plan.actions.size,
        lastAction = lastAction?.toString(),
        lastScreenSummary = "pkg=com.example | labels=Ready",
        recoveryAttempts = 1,
        status = status,
        savedAtMs = System.currentTimeMillis(),
        planFingerprint = PlanFingerprint.of(plan)
    )
}

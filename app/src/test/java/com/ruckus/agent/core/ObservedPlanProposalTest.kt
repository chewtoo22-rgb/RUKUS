package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedPlanProposalTest {
    private val screen = "pkg=com.example.app\ntext=Continue"

    @Test
    fun proposal_is_admitted_and_bound_to_same_observation() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
            nowEpochMs = 1_000L,
        ).getOrThrow()

        val decision = ObservedPlanFreshnessGate.evaluate(proposal, screen, nowEpochMs = 1_500L)
        assertTrue(decision.allowed)
    }

    @Test
    fun changed_ui_requires_reinspection_and_replanning() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
            nowEpochMs = 1_000L,
        ).getOrThrow()

        val decision = ObservedPlanFreshnessGate.evaluate(
            proposal,
            "pkg=com.example.app\ntext=Confirm purchase",
            nowEpochMs = 1_500L,
        )
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("replanning", ignoreCase = true))
    }

    @Test
    fun non_package_aware_observation_is_rejected() {
        val result = ObservedPlanProposal.create(
            goal = "Tap continue",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = "text=Continue",
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun proposal_still_obeys_bounded_plan_admission() {
        val actions = List(PlanAdmissionPolicy.MAX_ACTIONS + 1) { AgentAction.Home }
        val result = ObservedPlanProposal.create("Go home", actions, screen)
        assertTrue(result.isFailure)
    }

    @Test
    fun blank_and_oversized_goals_are_rejected() {
        assertTrue(ObservedPlanProposal.create("   ", listOf(AgentAction.Home), screen).isFailure)
        val oversized = "g".repeat(ObservedPlanProposal.MAX_GOAL_LENGTH + 1)
        assertTrue(ObservedPlanProposal.create(oversized, listOf(AgentAction.Home), screen).isFailure)
    }

    @Test
    fun action_mutation_after_admission_is_rejected() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
            nowEpochMs = 1_000L,
        ).getOrThrow()
        val tampered = proposal.copy(actions = listOf(AgentAction.Home))

        val decision = ObservedPlanFreshnessGate.evaluate(tampered, screen, nowEpochMs = 1_500L)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("changed after admission", ignoreCase = true))
    }

    @Test
    fun invalid_actions_cannot_bypass_admission_by_copying_a_proposal() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
            nowEpochMs = 1_000L,
        ).getOrThrow()
        val tampered = proposal.copy(actions = listOf(AgentAction.SetMediaVolume(101)))

        val decision = ObservedPlanFreshnessGate.evaluate(tampered, screen, nowEpochMs = 1_500L)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("admission", ignoreCase = true))
    }

    @Test
    fun expired_proposal_requires_reinspection_and_replanning() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
            nowEpochMs = 1_000L,
        ).getOrThrow()

        val decision = ObservedPlanFreshnessGate.evaluate(
            proposal,
            screen,
            nowEpochMs = 1_000L + ObservedPlanProposal.MAX_PROPOSAL_AGE_MS + 1L,
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("expired", ignoreCase = true))
    }

    @Test
    fun future_dated_proposal_is_rejected() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
            nowEpochMs = 2_000L,
        ).getOrThrow()

        val decision = ObservedPlanFreshnessGate.evaluate(proposal, screen, nowEpochMs = 1_999L)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("future", ignoreCase = true))
    }

    @Test
    fun timestamp_or_goal_mutation_invalidates_proposal_envelope() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
            nowEpochMs = 1_000L,
        ).getOrThrow()

        val timestampTampered = proposal.copy(issuedAtEpochMs = 1_200L)
        val goalTampered = proposal.copy(goal = "Go home")

        val timestampDecision = ObservedPlanFreshnessGate.evaluate(timestampTampered, screen, nowEpochMs = 1_500L)
        val goalDecision = ObservedPlanFreshnessGate.evaluate(goalTampered, screen, nowEpochMs = 1_500L)

        assertFalse(timestampDecision.allowed)
        assertFalse(goalDecision.allowed)
        assertTrue(timestampDecision.reason.contains("metadata changed", ignoreCase = true))
        assertTrue(goalDecision.reason.contains("metadata changed", ignoreCase = true))
    }
}

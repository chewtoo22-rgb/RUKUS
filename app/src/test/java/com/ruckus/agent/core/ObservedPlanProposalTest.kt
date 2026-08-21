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
        ).getOrThrow()

        val decision = ObservedPlanFreshnessGate.evaluate(proposal, screen)
        assertTrue(decision.allowed)
    }

    @Test
    fun changed_ui_requires_reinspection_and_replanning() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
        ).getOrThrow()

        val decision = ObservedPlanFreshnessGate.evaluate(
            proposal,
            "pkg=com.example.app\ntext=Confirm purchase",
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
}

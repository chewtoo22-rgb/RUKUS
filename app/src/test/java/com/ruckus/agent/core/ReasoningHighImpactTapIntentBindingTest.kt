package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningHighImpactTapIntentBindingTest {
    private fun observation(label: String) =
        "pkg=com.example.app | node[text=$label;clickable=true;enabled=true;editable=false;sensitive=false;focused=false;scrollable=false]"

    @Test
    fun routine_semantic_tap_does_not_require_goal_phrase_binding() {
        val decision = ReasoningIntentBindingPolicy.evaluate(
            "Continue with setup",
            listOf(AgentAction.TapLabel("Next")),
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun planner_cannot_invent_high_impact_tap_from_visible_ui() {
        val decision = ReasoningIntentBindingPolicy.evaluate(
            "Review this photo",
            listOf(AgentAction.TapLabel("Delete photo")),
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun exact_high_impact_control_phrase_in_goal_is_admitted_for_later_confirmation() {
        val decision = ReasoningIntentBindingPolicy.evaluate(
            "Delete photo after I approve the confirmation",
            listOf(AgentAction.TapLabel("Delete photo")),
        )

        assertTrue(decision.allowed)
        assertTrue(SafetyGate.classify(AgentAction.TapLabel("Delete photo")).risk == Risk.CONFIRM)
    }

    @Test
    fun high_impact_phrase_binding_uses_word_boundaries() {
        val decision = ReasoningIntentBindingPolicy.evaluate(
            "Open sender details",
            listOf(AgentAction.TapLabel("Send")),
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun observed_proposal_rejects_grounded_but_unrequested_high_impact_tap() {
        val proposal = ObservedPlanProposal.create(
            goal = "Inspect the photo options",
            actions = listOf(AgentAction.TapLabel("Delete photo")),
            observation = observation("Delete photo"),
            nowEpochMs = 100L,
        )

        assertTrue(proposal.isFailure)
    }

    @Test
    fun observed_proposal_accepts_requested_grounded_high_impact_tap_but_safety_still_requires_confirmation() {
        val proposal = ObservedPlanProposal.create(
            goal = "Delete photo",
            actions = listOf(AgentAction.TapLabel("Delete photo")),
            observation = observation("Delete photo"),
            nowEpochMs = 100L,
        )

        assertTrue(proposal.isSuccess)
        assertTrue(SafetyGate.classify(proposal.getOrThrow().actions.single()).risk == Risk.CONFIRM)
    }
}

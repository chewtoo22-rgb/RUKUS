package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningGroundingPolicyTest {
    @Test
    fun visible_semantic_target_is_allowed() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TapLabel("Continue")),
            "pkg=com.example.app | Cancel • Continue • Help",
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun target_matching_is_case_and_whitespace_tolerant() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TapLabel("  CONTINUE   NOW ")),
            "pkg=com.example.app\ntext=Continue now",
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun hallucinated_semantic_target_is_rejected() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TapLabel("Buy now")),
            "pkg=com.example.app | Cancel • Continue • Help",
        )
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("not visible", ignoreCase = true))
    }

    @Test
    fun no_readable_labels_cannot_ground_a_tap() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TapLabel("Continue")),
            "pkg=com.example.app | No readable labels",
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun non_targeted_bounded_actions_do_not_invent_ui_requirements() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.Home, AgentAction.Scroll(AgentAction.Direction.DOWN)),
            "pkg=com.example.app | No readable labels",
        )
        assertTrue(decision.allowed)
    }
}

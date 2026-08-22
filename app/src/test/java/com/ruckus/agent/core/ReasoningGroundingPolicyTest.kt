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
    fun structured_visible_semantic_target_is_allowed() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TapLabel("Continue")),
            "pkg=com.example.app | node[text=Cancel;clickable=true;editable=false;focused=false] • node[text=Continue;clickable=true;editable=false;focused=false]",
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
    fun typing_requires_a_focused_editable_node() {
        val allowed = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TypeText("hello")),
            "pkg=com.example.app | node[text=Message;clickable=true;editable=true;focused=true]",
        )
        assertTrue(allowed.allowed)

        val notFocused = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TypeText("hello")),
            "pkg=com.example.app | node[text=Message;clickable=true;editable=true;focused=false]",
        )
        assertFalse(notFocused.allowed)
        assertTrue(notFocused.reason.contains("focused editable", ignoreCase = true))

        val notEditable = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TypeText("hello")),
            "pkg=com.example.app | node[text=Message;clickable=true;editable=false;focused=true]",
        )
        assertFalse(notEditable.allowed)
    }

    @Test
    fun legacy_unstructured_observation_cannot_authorize_autonomous_typing() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.TypeText("hello")),
            "pkg=com.example.app | Message",
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun non_targeted_bounded_actions_do_not_invent_ui_requirements() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.Home),
            "pkg=com.example.app | No readable labels",
        )
        assertTrue(decision.allowed)
    }
}

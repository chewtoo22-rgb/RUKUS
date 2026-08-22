package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningScrollGroundingTest {
    @Test
    fun autonomous_scroll_is_allowed_when_exactly_one_enabled_scrollable_container_is_observed() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.Scroll(AgentAction.Direction.DOWN)),
            "pkg=com.example.app | node[text=Feed;clickable=false;enabled=true;editable=false;sensitive=false;focused=false;scrollable=true]",
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun autonomous_scroll_is_rejected_when_scrollable_container_is_disabled() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.Scroll(AgentAction.Direction.UP)),
            "pkg=com.example.app | node[text=Feed;clickable=false;enabled=false;editable=false;sensitive=false;focused=false;scrollable=true]",
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("enabled scrollable", ignoreCase = true))
    }

    @Test
    fun autonomous_scroll_is_rejected_when_no_scrollable_container_is_observed() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.Scroll(AgentAction.Direction.DOWN)),
            "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true;editable=false;sensitive=false;focused=false;scrollable=false]",
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun autonomous_scroll_is_rejected_when_multiple_enabled_scrollable_containers_are_observed() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.Scroll(AgentAction.Direction.DOWN)),
            "pkg=com.example.app | node[text=Feed;clickable=false;enabled=true;editable=false;sensitive=false;focused=false;scrollable=true] • node[text=Comments;clickable=false;enabled=true;editable=false;sensitive=false;focused=false;scrollable=true]",
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("ambiguous", ignoreCase = true))
    }

    @Test
    fun disabled_scrollable_duplicate_does_not_create_ambiguity() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.Scroll(AgentAction.Direction.UP)),
            "pkg=com.example.app | node[text=Feed;clickable=false;enabled=true;editable=false;sensitive=false;focused=false;scrollable=true] • node[text=Comments;clickable=false;enabled=false;editable=false;sensitive=false;focused=false;scrollable=true]",
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun missing_scrollable_metadata_fails_closed_for_autonomous_scroll() {
        val decision = ReasoningGroundingPolicy.evaluate(
            listOf(AgentAction.Scroll(AgentAction.Direction.DOWN)),
            "pkg=com.example.app | node[text=Feed;clickable=false;enabled=true;editable=false;sensitive=false;focused=false]",
        )

        assertFalse(decision.allowed)
    }
}

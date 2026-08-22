package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningTextIntentBindingTest {
    private val focusedEditableObservation =
        "pkg=com.example.app | node[text=Message;clickable=true;enabled=true;editable=true;sensitive=false;focused=true;scrollable=false]"

    @Test
    fun exact_text_present_in_goal_is_allowed() {
        val decision = ReasoningIntentBindingPolicy.evaluate(
            "Type hello Miyagi into the message field",
            listOf(AgentAction.TypeText("hello Miyagi")),
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun planner_cannot_invent_or_expand_text_payload() {
        val invented = ReasoningIntentBindingPolicy.evaluate(
            "Type hello into the message field",
            listOf(AgentAction.TypeText("hello there")),
        )
        val appended = ReasoningIntentBindingPolicy.evaluate(
            "Type hello",
            listOf(AgentAction.TypeText("hello!")),
        )

        assertFalse(invented.allowed)
        assertFalse(appended.allowed)
    }

    @Test
    fun text_binding_preserves_case_and_punctuation() {
        val wrongCase = ReasoningIntentBindingPolicy.evaluate(
            "Type Hello, Miyagi!",
            listOf(AgentAction.TypeText("hello, Miyagi!")),
        )
        val exact = ReasoningIntentBindingPolicy.evaluate(
            "Type Hello, Miyagi!",
            listOf(AgentAction.TypeText("Hello, Miyagi!")),
        )

        assertFalse(wrongCase.allowed)
        assertTrue(exact.allowed)
    }

    @Test
    fun observed_proposal_rejects_grounded_but_unrequested_text() {
        val proposal = ObservedPlanProposal.create(
            goal = "Write hello",
            actions = listOf(AgentAction.TypeText("hello from MIYAGI")),
            observation = focusedEditableObservation,
            nowEpochMs = 100L,
        )

        assertTrue(proposal.isFailure)
    }

    @Test
    fun observed_proposal_accepts_exact_requested_text_when_ui_is_safe() {
        val proposal = ObservedPlanProposal.create(
            goal = "Write hello from MIYAGI",
            actions = listOf(AgentAction.TypeText("hello from MIYAGI")),
            observation = focusedEditableObservation,
            nowEpochMs = 100L,
        )

        assertTrue(proposal.isSuccess)
    }
}

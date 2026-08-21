package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningPlanPolicyTest {
    private val screen = "pkg=com.example.app\ntext=Continue"

    @Test
    fun semantic_ui_plan_is_admitted() {
        val decision = ReasoningPlanPolicy.evaluate(
            listOf(
                AgentAction.TapLabel("Continue"),
                AgentAction.TypeText("hello"),
                AgentAction.InspectScreen,
            )
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun raw_coordinate_tap_is_not_admitted_from_reasoning_output() {
        val decision = ReasoningPlanPolicy.evaluate(listOf(AgentAction.Tap(100f, 200f)))

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("coordinate", ignoreCase = true))
    }

    @Test
    fun privileged_shell_is_not_admitted_from_reasoning_output() {
        val decision = ReasoningPlanPolicy.evaluate(listOf(AgentAction.RunApprovedShell("demo")))

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("privileged", ignoreCase = true))
    }

    @Test
    fun observed_proposal_refuses_raw_coordinate_tap_before_fingerprinting() {
        val result = ObservedPlanProposal.create(
            goal = "Tap the visible control",
            actions = listOf(AgentAction.Tap(100f, 200f)),
            observation = screen,
            nowEpochMs = 1_000L,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("coordinate", ignoreCase = true))
    }

    @Test
    fun observed_proposal_refuses_privileged_shell_before_fingerprinting() {
        val result = ObservedPlanProposal.create(
            goal = "Run a privileged command",
            actions = listOf(AgentAction.RunApprovedShell("demo")),
            observation = screen,
            nowEpochMs = 1_000L,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("privileged", ignoreCase = true))
    }
}

package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningPlanPolicyTest {
    private val screen = "pkg=com.example.app\ntext=Continue"

    @Test
    fun single_semantic_ui_mutation_is_admitted() {
        val decision = ReasoningPlanPolicy.evaluate(
            listOf(
                AgentAction.TapLabel("Continue"),
                AgentAction.InspectScreen,
            )
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun multiple_inspections_without_mutation_are_admitted() {
        val decision = ReasoningPlanPolicy.evaluate(
            listOf(AgentAction.InspectScreen, AgentAction.InspectScreen)
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun second_state_change_requires_fresh_observation_and_replan() {
        val decision = ReasoningPlanPolicy.evaluate(
            listOf(
                AgentAction.TapLabel("Continue"),
                AgentAction.TypeText("hello"),
            )
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("re-inspect", ignoreCase = true))
    }

    @Test
    fun observed_proposal_refuses_two_mutations_from_one_observation() {
        val result = ObservedPlanProposal.create(
            goal = "Continue and type hello",
            actions = listOf(
                AgentAction.TapLabel("Continue"),
                AgentAction.TypeText("hello"),
            ),
            observation = screen,
            nowEpochMs = 1_000L,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("re-inspect", ignoreCase = true))
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

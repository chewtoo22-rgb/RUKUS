package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningPlanPolicyTest {
    private val screen = "pkg=com.example.app\ntext=Continue"
    private val inventoryScreen = "pkg=com.example.app | app[package=com.example.target;label=Example]"

    @Test
    fun single_semantic_ui_mutation_is_admitted() {
        val decision = ReasoningPlanPolicy.evaluate(
            listOf(AgentAction.TapLabel("Continue"), AgentAction.InspectScreen)
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun multiple_inspections_without_mutation_are_admitted() {
        val decision = ReasoningPlanPolicy.evaluate(listOf(AgentAction.InspectScreen, AgentAction.InspectScreen))
        assertTrue(decision.allowed)
    }

    @Test
    fun second_state_change_requires_fresh_observation_and_replan() {
        val decision = ReasoningPlanPolicy.evaluate(
            listOf(AgentAction.TapLabel("Continue"), AgentAction.TypeText("hello"))
        )
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("re-inspect", ignoreCase = true))
    }

    @Test
    fun observed_proposal_refuses_two_mutations_from_one_observation() {
        val result = ObservedPlanProposal.create(
            goal = "Continue and type hello",
            actions = listOf(AgentAction.TapLabel("Continue"), AgentAction.TypeText("hello")),
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
    fun app_launch_primitives_are_admitted_to_the_reasoning_vocabulary() {
        assertTrue(ReasoningPlanPolicy.evaluate(listOf(AgentAction.OpenApp("com.example.target"))).allowed)
        assertTrue(ReasoningPlanPolicy.evaluate(listOf(AgentAction.OpenAppByName("Example"))).allowed)
    }

    @Test
    fun observed_proposal_refuses_app_launch_without_trusted_inventory_grounding() {
        val result = ObservedPlanProposal.create(
            goal = "Open Example",
            actions = listOf(AgentAction.OpenAppByName("Example")),
            observation = screen,
            nowEpochMs = 1_000L,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("trusted launchable-app inventory", ignoreCase = true))
    }

    @Test
    fun observed_proposal_admits_exact_package_from_trusted_inventory() {
        val result = ObservedPlanProposal.create(
            goal = "Open Example",
            actions = listOf(AgentAction.OpenApp("com.example.target")),
            observation = inventoryScreen,
            nowEpochMs = 1_000L,
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun observed_proposal_admits_unique_exact_app_label_from_trusted_inventory() {
        val result = ObservedPlanProposal.create(
            goal = "Open Example",
            actions = listOf(AgentAction.OpenAppByName("  example  ")),
            observation = inventoryScreen,
            nowEpochMs = 1_000L,
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun observed_proposal_refuses_unknown_package_even_when_inventory_exists() {
        val result = ObservedPlanProposal.create(
            goal = "Open Unknown",
            actions = listOf(AgentAction.OpenApp("com.example.unknown")),
            observation = inventoryScreen,
            nowEpochMs = 1_000L,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("does not contain that package", ignoreCase = true))
    }

    @Test
    fun observed_proposal_refuses_ambiguous_duplicate_app_labels() {
        val observation = "pkg=com.example.app | app[package=com.example.one;label=Example] • app[package=com.example.two;label=example]"
        val result = ObservedPlanProposal.create(
            goal = "Open Example",
            actions = listOf(AgentAction.OpenAppByName("Example")),
            observation = observation,
            nowEpochMs = 1_000L,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("share that label", ignoreCase = true))
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

package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningNavigationPolicyTest {
    private val observation = "pkg=com.example\nnode|text=Screen|clickable=false|enabled=true|editable=false|focused=false|sensitive=false|scrollable=false"

    @Test
    fun back_is_capability_admitted_but_requires_explicit_goal_intent() {
        val capability = ReasoningPlanPolicy.evaluate(listOf(AgentAction.Back))
        val unrelatedIntent = ReasoningIntentBindingPolicy.evaluate("Open settings", listOf(AgentAction.Back))
        val explicitIntent = ReasoningIntentBindingPolicy.evaluate("Go back", listOf(AgentAction.Back))

        assertTrue(capability.allowed)
        assertFalse(unrelatedIntent.allowed)
        assertTrue(explicitIntent.allowed)
    }

    @Test
    fun home_is_capability_admitted_but_requires_explicit_goal_intent() {
        val capability = ReasoningPlanPolicy.evaluate(listOf(AgentAction.Home))
        val unrelatedIntent = ReasoningIntentBindingPolicy.evaluate("Open settings", listOf(AgentAction.Home))
        val explicitIntent = ReasoningIntentBindingPolicy.evaluate("Return to the home screen", listOf(AgentAction.Home))

        assertTrue(capability.allowed)
        assertFalse(unrelatedIntent.allowed)
        assertTrue(explicitIntent.allowed)
    }

    @Test
    fun weak_navigation_words_do_not_authorize_global_navigation() {
        assertFalse(ReasoningIntentBindingPolicy.evaluate("I am back", listOf(AgentAction.Back)).allowed)
        assertFalse(ReasoningIntentBindingPolicy.evaluate("Show my home address", listOf(AgentAction.Home)).allowed)
    }

    @Test
    fun observed_proposals_admit_only_explicit_navigation_goals() {
        val back = ObservedPlanProposal.create("Press the back button", listOf(AgentAction.Back), observation, nowEpochMs = 10L)
        val home = ObservedPlanProposal.create("Go home", listOf(AgentAction.Home), observation, nowEpochMs = 10L)
        val incidental = ObservedPlanProposal.create("Open settings", listOf(AgentAction.Home), observation, nowEpochMs = 10L)

        assertTrue(back.isSuccess)
        assertTrue(home.isSuccess)
        assertTrue(incidental.isFailure)
    }

    @Test
    fun semantic_grounded_actions_remain_admitted_by_capability_policy() {
        val tap = ReasoningPlanPolicy.evaluate(listOf(AgentAction.TapLabel("Continue")))
        val inspect = ReasoningPlanPolicy.evaluate(listOf(AgentAction.InspectScreen))

        assertTrue(tap.allowed)
        assertTrue(inspect.allowed)
    }
}

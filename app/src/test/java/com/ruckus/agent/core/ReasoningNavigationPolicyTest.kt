package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningNavigationPolicyTest {
    @Test
    fun autonomous_back_is_rejected() {
        val decision = ReasoningPlanPolicy.evaluate(listOf(AgentAction.Back))

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("Back navigation"))
    }

    @Test
    fun autonomous_home_is_rejected() {
        val decision = ReasoningPlanPolicy.evaluate(listOf(AgentAction.Home))

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("Home navigation"))
    }

    @Test
    fun semantic_grounded_actions_remain_admitted_by_capability_policy() {
        val tap = ReasoningPlanPolicy.evaluate(listOf(AgentAction.TapLabel("Continue")))
        val inspect = ReasoningPlanPolicy.evaluate(listOf(AgentAction.InspectScreen))

        assertTrue(tap.allowed)
        assertTrue(inspect.allowed)
    }
}

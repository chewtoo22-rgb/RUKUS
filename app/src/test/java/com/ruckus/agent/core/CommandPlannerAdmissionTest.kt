package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPlannerAdmissionTest {
    @Test
    fun overHorizonPlanExposesNoExecutableActions() {
        val request = List(PlanAdmissionPolicy.MAX_ACTIONS + 1) { "home" }.joinToString(" then ")

        val plan = CommandPlanner.plan(request)

        assertTrue(plan.actions.isEmpty())
        assertEquals(1, plan.rejectedParts.size)
        assertTrue(plan.rejectedParts.single().contains("maximum bounded horizon", ignoreCase = true))
    }

    @Test
    fun admittedMaxHorizonPlanRemainsExecutable() {
        val request = List(PlanAdmissionPolicy.MAX_ACTIONS) { "home" }.joinToString(" then ")

        val plan = CommandPlanner.plan(request)

        assertEquals(PlanAdmissionPolicy.MAX_ACTIONS, plan.actions.size)
        assertTrue(plan.rejectedParts.isEmpty())
    }

    @Test
    fun oversizedGoalExposesNoExecutableActionsBeforeParsing() {
        val request = "home " + "x".repeat(GoalAdmissionPolicy.MAX_GOAL_CHARS)

        val plan = CommandPlanner.plan(request)

        assertTrue(plan.actions.isEmpty())
        assertEquals(1, plan.rejectedParts.size)
        assertTrue(plan.rejectedParts.single().contains("character limit", ignoreCase = true))
    }

    @Test
    fun controlCharacterGoalFailsClosed() {
        val plan = CommandPlanner.plan("home\u0000then back")

        assertTrue(plan.actions.isEmpty())
        assertEquals(1, plan.rejectedParts.size)
        assertTrue(plan.rejectedParts.single().contains("control characters", ignoreCase = true))
    }

    @Test
    fun surroundingWhitespaceIsNormalizedBeforePlanning() {
        val plan = CommandPlanner.plan("  home then back  ")

        assertEquals(listOf(AgentAction.Home, AgentAction.Back), plan.actions)
        assertTrue(plan.rejectedParts.isEmpty())
    }

    @Test
    fun unknownSegmentStillReturnsKnownActionsButMarksPlanIncomplete() {
        val plan = CommandPlanner.plan("home then make coffee")

        assertEquals(listOf(AgentAction.Home), plan.actions)
        assertEquals(listOf("make coffee"), plan.rejectedParts)
    }
}

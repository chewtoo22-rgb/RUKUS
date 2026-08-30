package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalAdmissionPolicyDeviceTest {
    @Test
    fun oversizedGoalNeverExposesControllerActions() {
        val request = "home " + "x".repeat(GoalAdmissionPolicy.MAX_GOAL_CHARS)

        val plan = CommandPlanner.plan(request)

        assertTrue(plan.actions.isEmpty())
        assertEquals(1, plan.rejectedParts.size)
        assertTrue(plan.rejectedParts.single().contains("character limit", ignoreCase = true))
    }

    @Test
    fun boundedGoalStillProducesTypedActions() {
        val plan = CommandPlanner.plan("home then back")

        assertEquals(listOf(AgentAction.Home, AgentAction.Back), plan.actions)
        assertTrue(plan.rejectedParts.isEmpty())
    }
}

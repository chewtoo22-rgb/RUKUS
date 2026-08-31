package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPlannerArgumentIntegrityTest {
    @Test
    fun plainAndInsideTypedTextIsPreservedAsUserData() {
        val plan = CommandPlanner.plan("type rock and roll")

        assertTrue(plan.rejectedParts.isEmpty())
        assertEquals(1, plan.actions.size)
        assertEquals(AgentAction.TypeText("rock and roll"), plan.actions.single())
    }

    @Test
    fun plainAndInsideAppNameIsPreservedAsUserData() {
        val plan = CommandPlanner.plan("open Barnes and Noble")

        assertTrue(plan.rejectedParts.isEmpty())
        assertEquals(1, plan.actions.size)
        assertEquals(AgentAction.OpenAppByName("Barnes and Noble"), plan.actions.single())
    }

    @Test
    fun ordinaryThenInsideTypedTextIsPreservedAsUserData() {
        val plan = CommandPlanner.plan("type better then ever")

        assertTrue(plan.rejectedParts.isEmpty())
        assertEquals(1, plan.actions.size)
        assertEquals(AgentAction.TypeText("better then ever"), plan.actions.single())
    }

    @Test
    fun ordinaryThenInsideAppNameIsPreservedAsUserData() {
        val plan = CommandPlanner.plan("open Better Then Ezra")

        assertTrue(plan.rejectedParts.isEmpty())
        assertEquals(1, plan.actions.size)
        assertEquals(AgentAction.OpenAppByName("Better Then Ezra"), plan.actions.single())
    }

    @Test
    fun explicitThenStillBuildsDeterministicSequence() {
        val plan = CommandPlanner.plan("home then type rock and roll")

        assertTrue(plan.rejectedParts.isEmpty())
        assertEquals(listOf(AgentAction.Home, AgentAction.TypeText("rock and roll")), plan.actions)
    }

    @Test
    fun explicitAndThenStillBuildsDeterministicSequence() {
        val plan = CommandPlanner.plan("home and then open Barnes and Noble")

        assertTrue(plan.rejectedParts.isEmpty())
        assertEquals(listOf(AgentAction.Home, AgentAction.OpenAppByName("Barnes and Noble")), plan.actions)
    }

    @Test
    fun thenBeforeSupportedCommandStillCreatesBoundary() {
        val plan = CommandPlanner.plan("type done then home")

        assertTrue(plan.rejectedParts.isEmpty())
        assertEquals(listOf(AgentAction.TypeText("done"), AgentAction.Home), plan.actions)
    }
}

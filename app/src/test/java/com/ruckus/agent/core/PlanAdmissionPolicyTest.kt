package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class PlanAdmissionPolicyTest {
    @Test fun admitsSmallWellFormedPlans() {
        val decision=PlanAdmissionPolicy.evaluate(
            listOf(
                AgentAction.OpenAppByName("Spotify"),
                AgentAction.TapLabel("Search"),
                AgentAction.TypeText("Deftones")
            )
        )
        assertTrue(decision.allowed)
    }

    @Test fun rejectsPlansBeyondBoundedHorizon() {
        val actions=List(PlanAdmissionPolicy.MAX_ACTIONS + 1) { AgentAction.Home }
        val decision=PlanAdmissionPolicy.evaluate(actions)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("maximum bounded horizon"))
    }

    @Test fun rejectsMalformedTargetsAndPayloads() {
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.TapLabel("   "))).allowed)
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.OpenAppByName(""))).allowed)
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.TypeText(""))).allowed)
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.SetMediaVolume(101))).allowed)
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.Swipe(0f,0f,1f,1f,0))).allowed)
    }

    @Test fun rejectsMalformedCoordinateActionsBeforeExecution() {
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.Tap(Float.NaN, 100f))).allowed)
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.Tap(100f, Float.POSITIVE_INFINITY))).allowed)
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.Tap(-1f, 100f))).allowed)
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.Swipe(10f, 10f, 10f, 10f, 350))).allowed)
        assertFalse(PlanAdmissionPolicy.evaluate(listOf(AgentAction.Swipe(10f, 10f, Float.NaN, 20f, 350))).allowed)
        assertTrue(PlanAdmissionPolicy.evaluate(listOf(AgentAction.Tap(10f, 20f))).allowed)
        assertTrue(PlanAdmissionPolicy.evaluate(listOf(AgentAction.Swipe(10f, 10f, 20f, 30f, 350))).allowed)
    }

    @Test fun commandPlannerRefusesOverlongRequestAtomically() {
        val request=(1..(PlanAdmissionPolicy.MAX_ACTIONS + 1)).joinToString(" then ") { "home" }
        val plan=CommandPlanner.plan(request)
        assertEquals(PlanAdmissionPolicy.MAX_ACTIONS + 1,plan.actions.size)
        assertTrue(plan.rejectedParts.any { it.contains("plan rejected") })
    }

    @Test fun shellArgumentsAreStructurallyBoundedBeforeSafetyGate() {
        val tooMany=(1..(PlanAdmissionPolicy.MAX_SHELL_ARGS + 1)).associate { "k$it" to "v$it" }
        val decision=PlanAdmissionPolicy.evaluate(listOf(AgentAction.RunApprovedShell("demo",tooMany)))
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("too many arguments"))
    }
}

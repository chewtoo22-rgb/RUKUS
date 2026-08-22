package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class TaskCompletionRepairPlannerTest {
    @Test fun exactPackageGoalGetsExactRepair() {
        val original=AgentAction.OpenApp("com.spotify.music")
        val repair=TaskCompletionRepairPlanner.plan(original,"foreground drifted")
        assertEquals(original,repair.action)
        assertEquals(Risk.SAFE,SafetyGate.classify(repair.action!!).risk)
    }

    @Test fun namedAppGoalGetsExactRepair() {
        val original=AgentAction.OpenAppByName("Spotify")
        val repair=TaskCompletionRepairPlanner.plan(original,"foreground unavailable")
        assertEquals(original,repair.action)
    }

    @Test fun typedTextGoalGetsOneExactRepair() {
        val original=AgentAction.TypeText("hello")
        val repair=TaskCompletionRepairPlanner.plan(original,"text evidence disappeared")
        assertEquals(original,repair.action)
    }

    @Test fun consequentialOrAmbiguousTerminalActionsAreNotReplayed() {
        assertNull(TaskCompletionRepairPlanner.plan(AgentAction.TapLabel("Pay"),"checkpoint missing").action)
        assertNull(TaskCompletionRepairPlanner.plan(AgentAction.RunApprovedShell("demo"),"checkpoint missing").action)
        assertNull(TaskCompletionRepairPlanner.plan(AgentAction.SetBrightness(50),"checkpoint missing").action)
    }

    @Test fun completionRepairStillConsumesRecoveryBudget() {
        assertTrue(RecoveryBudget.decide(RecoveryBudget.MAX_TOTAL_ATTEMPTS-1).allowed)
        assertFalse(RecoveryBudget.decide(RecoveryBudget.MAX_TOTAL_ATTEMPTS).allowed)
    }
}

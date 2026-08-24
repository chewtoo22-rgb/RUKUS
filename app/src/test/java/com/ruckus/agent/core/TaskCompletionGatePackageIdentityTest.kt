package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class TaskCompletionGatePackageIdentityTest {
    private fun plan(action: AgentAction) = CommandPlanner.Plan(listOf(action), emptyList())

    @Test fun exactForegroundPackageCompletesOpenAppTask() {
        val decision = TaskCompletionGate.evaluate(
            plan(AgentAction.OpenApp("com.example.app")),
            completedSteps = 1,
            finalScreen = "pkg=com.example.app state[brightness=128]"
        )
        assertTrue(decision.ok)
    }

    @Test fun packagePrefixSpoofDoesNotCompleteOpenAppTask() {
        val decision = TaskCompletionGate.evaluate(
            plan(AgentAction.OpenApp("com.example.app")),
            completedSteps = 1,
            finalScreen = "pkg=com.example.app.evil state[brightness=128]"
        )
        assertFalse(decision.ok)
    }

    @Test fun packageTokenStopsAtWhitespaceOrMetadataDelimiter() {
        val whitespace = TaskCompletionGate.evaluate(
            plan(AgentAction.OpenApp("com.example.app")),
            1,
            "pkg=com.example.app text=Home"
        )
        val delimiter = TaskCompletionGate.evaluate(
            plan(AgentAction.OpenApp("com.example.app")),
            1,
            "pkg=com.example.app|text=Home"
        )
        assertTrue(whitespace.ok)
        assertTrue(delimiter.ok)
    }

    @Test fun malformedOrMissingPackageFailsClosed() {
        assertFalse(TaskCompletionGate.evaluate(plan(AgentAction.OpenApp("com.example.app")), 1, "pkg=").ok)
        assertFalse(TaskCompletionGate.evaluate(plan(AgentAction.OpenApp("com.example.app")), 1, "text=Home").ok)
        assertFalse(TaskCompletionGate.evaluate(plan(AgentAction.OpenApp("com.example.app")), 1, null).ok)
    }
}

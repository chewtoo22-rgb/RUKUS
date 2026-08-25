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

    @Test fun appNameCompletionRequiresForegroundPackageBoundToUniqueExactLabel() {
        val finalScreen = "pkg=com.spotify.music | app[package=com.spotify.music;label=Spotify] • app[package=com.google.android.youtube;label=YouTube]"
        val decision = TaskCompletionGate.evaluate(
            plan(AgentAction.OpenAppByName(" spotify ")),
            1,
            finalScreen
        )
        assertTrue(decision.ok)
    }

    @Test fun unrelatedForegroundPackageCannotCompleteAppNameTask() {
        val finalScreen = "pkg=com.google.android.youtube | app[package=com.spotify.music;label=Spotify] • app[package=com.google.android.youtube;label=YouTube]"
        val decision = TaskCompletionGate.evaluate(
            plan(AgentAction.OpenAppByName("Spotify")),
            1,
            finalScreen
        )
        assertFalse(decision.ok)
    }

    @Test fun ambiguousAppLabelCannotBecomeCompletionProof() {
        val finalScreen = "pkg=com.example.photos.one | app[package=com.example.photos.one;label=Photos] • app[package=com.example.photos.two;label=Photos]"
        val decision = TaskCompletionGate.evaluate(
            plan(AgentAction.OpenAppByName("Photos")),
            1,
            finalScreen
        )
        assertFalse(decision.ok)
    }

    @Test fun appNameCompletionRequiresLaunchableInventoryEvidence() {
        val decision = TaskCompletionGate.evaluate(
            plan(AgentAction.OpenAppByName("Spotify")),
            1,
            "pkg=com.spotify.music | node[text=Spotify;clickable=false;enabled=true]"
        )
        assertFalse(decision.ok)
    }
}

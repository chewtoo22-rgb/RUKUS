package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskCompletionGateAppIdentityDeviceTest {
    private fun plan(action: AgentAction) = CommandPlanner.Plan(listOf(action), emptyList())

    @Test
    fun appNameCompletionIsBoundToExactForegroundPackage() {
        val matching = "pkg=com.spotify.music | app[package=com.spotify.music;label=Spotify] • app[package=com.google.android.youtube;label=YouTube]"
        val wrongForeground = "pkg=com.google.android.youtube | app[package=com.spotify.music;label=Spotify] • app[package=com.google.android.youtube;label=YouTube]"

        assertTrue(TaskCompletionGate.evaluate(plan(AgentAction.OpenAppByName("Spotify")), 1, matching).ok)
        assertFalse(TaskCompletionGate.evaluate(plan(AgentAction.OpenAppByName("Spotify")), 1, wrongForeground).ok)
    }

    @Test
    fun ambiguousLaunchableLabelFailsClosed() {
        val ambiguous = "pkg=com.example.photos.one | app[package=com.example.photos.one;label=Photos] • app[package=com.example.photos.two;label=Photos]"

        assertFalse(TaskCompletionGate.evaluate(plan(AgentAction.OpenAppByName("Photos")), 1, ambiguous).ok)
    }
}

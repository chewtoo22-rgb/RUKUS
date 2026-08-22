package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedAppLaunchCanonicalizationTest {
    private val observation = "pkg=com.example.home | app[package=com.spotify.music;label=Spotify] • app[package=com.google.android.youtube;label=YouTube]"

    @Test
    fun grounded_app_name_is_canonicalized_to_exact_package() {
        val proposal = ObservedPlanProposal.create(
            goal = "Open Spotify",
            actions = listOf(AgentAction.OpenAppByName(" spotify ")),
            observation = observation,
            nowEpochMs = 1_000L,
        ).getOrThrow()

        assertEquals(listOf(AgentAction.OpenApp("com.spotify.music")), proposal.actions)
        assertTrue(ObservedPlanFreshnessGate.evaluate(proposal, observation, nowEpochMs = 1_500L).allowed)
    }

    @Test
    fun unknown_app_name_still_fails_closed_before_canonicalization() {
        val result = ObservedPlanProposal.create(
            goal = "Open Maps",
            actions = listOf(AgentAction.OpenAppByName("Maps")),
            observation = observation,
            nowEpochMs = 1_000L,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun duplicate_app_labels_cannot_be_canonicalized() {
        val ambiguous = "pkg=com.example.home | app[package=com.example.one;label=Photos] • app[package=com.example.two;label=Photos]"
        val result = ObservedPlanProposal.create(
            goal = "Open Photos",
            actions = listOf(AgentAction.OpenAppByName("Photos")),
            observation = ambiguous,
            nowEpochMs = 1_000L,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun tampering_canonical_package_after_admission_is_rejected() {
        val proposal = ObservedPlanProposal.create(
            goal = "Open Spotify",
            actions = listOf(AgentAction.OpenAppByName("Spotify")),
            observation = observation,
            nowEpochMs = 1_000L,
        ).getOrThrow()
        val tampered = proposal.copy(actions = listOf(AgentAction.OpenApp("com.google.android.youtube")))

        val decision = ObservedPlanFreshnessGate.evaluate(tampered, observation, nowEpochMs = 1_500L)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("changed after admission", ignoreCase = true))
    }
}

package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenObservationSettlerTest {

    @Test
    fun transientChangeThenBaselineDoesNotSettleAsPostActionUi() {
        val samples = ArrayDeque(listOf(
            "pkg=com.example.next | screen=Transient",
            "pkg=com.example.old | screen=Before",
            "pkg=com.example.old | screen=Before"
        ))

        val result = ScreenObservationSettler.observe(
            before = "pkg=com.example.old | screen=Before",
            maxSamples = 3,
            requiredStableSamples = 2,
            sampler = { samples.removeFirst() }
        )

        assertFalse(result.stable)
        assertTrue(result.changedFromBefore)
        assertEquals(3, result.samples)
        assertEquals("pkg=com.example.old | screen=Before", result.screen)
    }

    @Test
    fun unstableChangedFramesAreNotExposedAsVerificationEvidence() {
        val before = "pkg=com.example.old | screen=Before"
        val samples = ArrayDeque(listOf(
            "pkg=com.example.next | screen=Loading",
            "pkg=com.example.next | screen=Intermediate",
            "pkg=com.example.next | screen=AlmostThere"
        ))

        val result = ScreenObservationSettler.observe(
            before = before,
            maxSamples = 3,
            requiredStableSamples = 2,
            sampler = { samples.removeFirst() }
        )

        assertFalse(result.stable)
        assertTrue(result.changedFromBefore)
        assertEquals(3, result.samples)
        assertEquals(before, result.screen)
    }

    @Test
    fun stableChangedUiSettlesAfterTwoMatchingPostChangeSamples() {
        val samples = ArrayDeque(listOf(
            "pkg=com.example.old | screen=Before",
            "pkg=com.example.next | screen=After",
            "pkg=com.example.next | screen=After"
        ))

        val result = ScreenObservationSettler.observe(
            before = "pkg=com.example.old | screen=Before",
            maxSamples = 5,
            requiredStableSamples = 2,
            sampler = { samples.removeFirst() }
        )

        assertTrue(result.stable)
        assertTrue(result.changedFromBefore)
        assertEquals(3, result.samples)
        assertEquals("pkg=com.example.next | screen=After", result.screen)
    }
}

package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android-runtime probe for settle/inspect determinism.
 *
 * CI compiles this into the instrumentation APK. Execution remains a Thursday
 * device/emulator test so no physical-runtime result is implied by compilation.
 */
@RunWith(AndroidJUnit4::class)
class ScreenObservationSettlerDeviceTest {

    @Test
    fun transientFrameCannotAuthorizeSettlingBackOnPreActionUi() {
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
    fun twoStablePostChangeSnapshotsAreAccepted() {
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

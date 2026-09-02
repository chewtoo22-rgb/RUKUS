package com.ruckus.agent.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionReadinessReleaseContractTest {
    private val ready = PermissionReadiness.Snapshot(
        accessibilityReady = true,
        writeSettingsReady = true,
        shizukuReady = true,
        observedAtElapsedRealtimeMs = 10_000L,
    )

    @Test
    fun exactAgeLimitRemainsAdmitted() {
        val decision = PermissionReadiness.evaluate(
            snapshot = ready,
            required = PermissionReadiness.Capability.entries.toSet(),
            nowElapsedRealtimeMs = 15_000L,
        )

        assertTrue(decision.ready)
        assertTrue(decision.blockers.isEmpty())
    }

    @Test
    fun staleObservationWinsOverCapabilityFailures() {
        val decision = PermissionReadiness.evaluate(
            snapshot = ready.copy(accessibilityReady = false),
            required = setOf(PermissionReadiness.Capability.ACCESSIBILITY),
            nowElapsedRealtimeMs = 15_001L,
        )

        assertFalse(decision.ready)
        assertEquals(
            listOf(PermissionReadiness.BlockReason.STALE_OBSERVATION),
            decision.blockers,
        )
    }

    @Test
    fun invalidClockInputFailsClosed() {
        val decision = PermissionReadiness.evaluate(
            snapshot = ready.copy(observedAtElapsedRealtimeMs = 20_000L),
            required = emptySet(),
            nowElapsedRealtimeMs = 19_999L,
        )

        assertFalse(decision.ready)
        assertEquals(
            listOf(PermissionReadiness.BlockReason.INVALID_TIME),
            decision.blockers,
        )
    }

    @Test
    fun allMissingCapabilitiesKeepStableOrdering() {
        val decision = PermissionReadiness.evaluate(
            snapshot = ready.copy(
                accessibilityReady = false,
                writeSettingsReady = false,
                shizukuReady = false,
            ),
            required = PermissionReadiness.Capability.entries.toSet(),
            nowElapsedRealtimeMs = 10_000L,
        )

        assertFalse(decision.ready)
        assertEquals(
            listOf(
                PermissionReadiness.BlockReason.ACCESSIBILITY_UNAVAILABLE,
                PermissionReadiness.BlockReason.WRITE_SETTINGS_UNAVAILABLE,
                PermissionReadiness.BlockReason.SHIZUKU_UNAVAILABLE,
            ),
            decision.blockers,
        )
    }
}

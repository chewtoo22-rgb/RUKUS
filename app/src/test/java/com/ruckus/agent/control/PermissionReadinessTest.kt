package com.ruckus.agent.control

import com.ruckus.agent.control.PermissionReadiness.BlockReason
import com.ruckus.agent.control.PermissionReadiness.Capability
import com.ruckus.agent.control.PermissionReadiness.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionReadinessTest {
    private val readySnapshot = Snapshot(
        accessibilityReady = true,
        writeSettingsReady = true,
        shizukuReady = true,
        observedAtElapsedRealtimeMs = 10_000,
    )

    @Test
    fun allRequiredCapabilitiesReady() {
        val decision = PermissionReadiness.evaluate(
            snapshot = readySnapshot,
            required = Capability.entries.toSet(),
            nowElapsedRealtimeMs = 12_000,
        )

        assertTrue(decision.ready)
        assertTrue(decision.blockers.isEmpty())
    }

    @Test
    fun missingCapabilitiesAreReportedDeterministically() {
        val decision = PermissionReadiness.evaluate(
            snapshot = readySnapshot.copy(accessibilityReady = false, shizukuReady = false),
            required = Capability.entries.toSet(),
            nowElapsedRealtimeMs = 12_000,
        )

        assertFalse(decision.ready)
        assertEquals(
            listOf(BlockReason.ACCESSIBILITY_UNAVAILABLE, BlockReason.SHIZUKU_UNAVAILABLE),
            decision.blockers,
        )
    }

    @Test
    fun unrequiredCapabilityDoesNotBlock() {
        val decision = PermissionReadiness.evaluate(
            snapshot = readySnapshot.copy(shizukuReady = false),
            required = setOf(Capability.ACCESSIBILITY),
            nowElapsedRealtimeMs = 12_000,
        )

        assertTrue(decision.ready)
    }

    @Test
    fun staleObservationFailsClosedBeforeCapabilityChecks() {
        val decision = PermissionReadiness.evaluate(
            snapshot = readySnapshot.copy(accessibilityReady = false),
            required = setOf(Capability.ACCESSIBILITY),
            nowElapsedRealtimeMs = 15_001,
            maxAgeMs = 5_000,
        )

        assertEquals(listOf(BlockReason.STALE_OBSERVATION), decision.blockers)
    }

    @Test
    fun observationExactlyAtAgeLimitIsAccepted() {
        val decision = PermissionReadiness.evaluate(
            snapshot = readySnapshot,
            required = Capability.entries.toSet(),
            nowElapsedRealtimeMs = 15_000,
            maxAgeMs = 5_000,
        )

        assertTrue(decision.ready)
    }

    @Test
    fun futureObservationFailsClosed() {
        val decision = PermissionReadiness.evaluate(
            snapshot = readySnapshot.copy(observedAtElapsedRealtimeMs = 20_000),
            required = emptySet(),
            nowElapsedRealtimeMs = 19_999,
        )

        assertEquals(listOf(BlockReason.INVALID_TIME), decision.blockers)
    }

    @Test
    fun negativeMonotonicTimeFailsClosed() {
        val decision = PermissionReadiness.evaluate(
            snapshot = readySnapshot.copy(observedAtElapsedRealtimeMs = -1),
            required = emptySet(),
            nowElapsedRealtimeMs = 0,
        )

        assertEquals(listOf(BlockReason.INVALID_TIME), decision.blockers)
    }

    @Test
    fun excessiveFreshnessWindowIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionReadiness.evaluate(
                snapshot = readySnapshot,
                required = emptySet(),
                nowElapsedRealtimeMs = 10_000,
                maxAgeMs = PermissionReadiness.MAX_ALLOWED_AGE_MS + 1,
            )
        }
    }

    @Test
    fun negativeFreshnessWindowIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PermissionReadiness.evaluate(
                snapshot = readySnapshot,
                required = emptySet(),
                nowElapsedRealtimeMs = 10_000,
                maxAgeMs = -1,
            )
        }
    }
}

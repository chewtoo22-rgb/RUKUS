package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android-runtime probes for the short-lived privileged confirmation lease.
 *
 * A persisted WAITING_CONFIRMATION checkpoint is evidence of what the user was
 * asked to approve, not indefinite execution authority. These probes lock the
 * clock boundary down on the same Android test path used for Thursday device
 * validation without dispatching any privileged action.
 */
class ConfirmationLeaseDeviceTest {

    @Test
    fun freshConfirmationRemainsValidInsideLease() {
        val now = 1_000_000L
        val decision = ConfirmationLeasePolicy.evaluate(
            savedAtMs = now - ConfirmationLeasePolicy.MAX_AGE_MS + 1L,
            nowMs = now
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun expiredConfirmationFailsClosedAfterLeaseBoundary() {
        val now = 1_000_000L
        val decision = ConfirmationLeasePolicy.evaluate(
            savedAtMs = now - ConfirmationLeasePolicy.MAX_AGE_MS - 1L,
            nowMs = now
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("expired", ignoreCase = true))
    }

    @Test
    fun futureDatedConfirmationFailsClosed() {
        val now = 1_000_000L
        val decision = ConfirmationLeasePolicy.evaluate(
            savedAtMs = now + 1L,
            nowMs = now
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("future", ignoreCase = true))
    }
}

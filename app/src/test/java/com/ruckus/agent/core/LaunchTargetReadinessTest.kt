package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTargetReadinessTest {
    @Test
    fun availableTargetPasses() {
        assertTrue(LaunchTargetReadiness.probe { true })
    }

    @Test
    fun unavailableTargetFailsClosed() {
        assertFalse(LaunchTargetReadiness.probe { false })
    }

    @Test
    fun securityExceptionFailsClosed() {
        assertFalse(LaunchTargetReadiness.probe { throw SecurityException("package visibility denied") })
    }

    @Test
    fun runtimeResolverFailureFailsClosed() {
        assertFalse(LaunchTargetReadiness.probe { throw IllegalStateException("resolver unavailable") })
    }
}

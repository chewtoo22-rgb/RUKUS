package com.ruckus.agent.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceExecutionBoundaryTest {
    @Test
    fun `successful value is preserved`() {
        assertEquals("ok", DeviceExecutionBoundary.capture { "ok" }.getOrThrow())
    }

    @Test
    fun `recoverable exception becomes result failure`() {
        val failure = IllegalStateException("android rejected operation")

        val result = DeviceExecutionBoundary.capture<String> { throw failure }

        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `fatal error is not disguised as command failure`() {
        val failure = AssertionError("fatal")

        val thrown = assertThrows(AssertionError::class.java) {
            DeviceExecutionBoundary.capture<String> { throw failure }
        }

        assertSame(failure, thrown)
    }
}

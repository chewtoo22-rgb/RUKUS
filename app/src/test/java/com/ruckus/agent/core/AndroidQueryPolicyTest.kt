package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidQueryPolicyTest {
    @Test
    fun `successful query preserves results`() {
        assertEquals(listOf("a", "b"), AndroidQueryPolicy.readOrEmpty { listOf("a", "b") })
    }

    @Test
    fun `security failure degrades to empty diagnostic inventory`() {
        assertEquals(
            emptyList<String>(),
            AndroidQueryPolicy.readOrEmpty<String> { throw SecurityException("hidden") },
        )
    }

    @Test
    fun `runtime platform failure degrades to empty diagnostic inventory`() {
        assertEquals(
            emptyList<String>(),
            AndroidQueryPolicy.readOrEmpty<String> { throw IllegalStateException("pm unavailable") },
        )
    }

    @Test
    fun `successful scalar query preserves value`() {
        assertEquals(42, AndroidQueryPolicy.readOrDefault(-1) { 42 })
    }

    @Test
    fun `runtime scalar failure uses diagnostic default`() {
        assertEquals(-1, AndroidQueryPolicy.readOrDefault(-1) { throw SecurityException("hidden") })
    }

    @Test(expected = AssertionError::class)
    fun `fatal scalar failure is not disguised as missing telemetry`() {
        AndroidQueryPolicy.readOrDefault(-1) { throw AssertionError("fatal") }
    }
}

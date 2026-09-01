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
        assertEquals(emptyList<String>(), AndroidQueryPolicy.readOrEmpty { throw SecurityException("hidden") })
    }

    @Test
    fun `runtime platform failure degrades to empty diagnostic inventory`() {
        assertEquals(emptyList<String>(), AndroidQueryPolicy.readOrEmpty { throw IllegalStateException("pm unavailable") })
    }
}

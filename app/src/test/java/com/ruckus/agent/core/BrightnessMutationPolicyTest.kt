package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BrightnessMutationPolicyTest {
    @Test
    fun `percent endpoints map exactly to android brightness range`() {
        assertEquals(0, BrightnessMutationPolicy.toSystemValue(0))
        assertEquals(255, BrightnessMutationPolicy.toSystemValue(100))
    }

    @Test
    fun `midpoint uses deterministic integer mapping`() {
        assertEquals(127, BrightnessMutationPolicy.toSystemValue(50))
    }

    @Test
    fun `out of range brightness is rejected before platform write`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrightnessMutationPolicy.toSystemValue(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BrightnessMutationPolicy.toSystemValue(101)
        }
    }

    @Test
    fun `provider rejection is surfaced as execution failure`() {
        BrightnessMutationPolicy.requireApplied(true, 42)

        val failure = assertThrows(IllegalStateException::class.java) {
            BrightnessMutationPolicy.requireApplied(false, 42)
        }
        assertEquals("Android rejected brightness change to 42%", failure.message)
    }
}

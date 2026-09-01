package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaVolumeMutationPolicyTest {
    @Test
    fun `percent endpoints map exactly to available media range`() {
        assertEquals(0, MediaVolumeMutationPolicy.targetIndex(0, 15))
        assertEquals(15, MediaVolumeMutationPolicy.targetIndex(100, 15))
    }

    @Test
    fun `midpoint uses deterministic integer mapping`() {
        assertEquals(7, MediaVolumeMutationPolicy.targetIndex(50, 15))
    }

    @Test
    fun `mapping uses long arithmetic before narrowing`() {
        assertEquals(Int.MAX_VALUE, MediaVolumeMutationPolicy.targetIndex(100, Int.MAX_VALUE))
    }

    @Test
    fun `invalid inputs are rejected before platform mutation`() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaVolumeMutationPolicy.targetIndex(-1, 15)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MediaVolumeMutationPolicy.targetIndex(101, 15)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MediaVolumeMutationPolicy.targetIndex(50, -1)
        }
    }

    @Test
    fun `readback mismatch is surfaced as execution failure`() {
        MediaVolumeMutationPolicy.requireApplied(expectedIndex = 9, actualIndex = 9, percent = 60)

        val failure = assertThrows(IllegalStateException::class.java) {
            MediaVolumeMutationPolicy.requireApplied(expectedIndex = 9, actualIndex = 8, percent = 60)
        }
        assertEquals(
            "Android rejected media volume change to 60% (expected index=9, actual=8)",
            failure.message
        )
    }
}

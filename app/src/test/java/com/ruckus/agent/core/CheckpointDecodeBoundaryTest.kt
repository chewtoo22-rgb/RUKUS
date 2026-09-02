package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckpointDecodeBoundaryTest {
    @Test
    fun `successful checkpoint decode is preserved`() {
        assertEquals("decoded", CheckpointDecodeBoundary.decodeOrNull { "decoded" })
    }

    @Test
    fun `malformed checkpoint exception fails closed`() {
        assertNull(
            CheckpointDecodeBoundary.decodeOrNull<String> {
                throw IllegalArgumentException("bad persisted checkpoint")
            }
        )
    }

    @Test(expected = LinkageError::class)
    fun `fatal linkage error is never disguised as missing checkpoint`() {
        CheckpointDecodeBoundary.decodeOrNull<String> {
            throw LinkageError("broken runtime")
        }
    }

    @Test(expected = AssertionError::class)
    fun `fatal assertion error propagates`() {
        CheckpointDecodeBoundary.decodeOrNull<String> {
            throw AssertionError("fatal invariant")
        }
    }
}

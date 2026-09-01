package com.ruckus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverableAndroidCallTest {
    @Test
    fun successfulCallIsPreserved() {
        val result = runCatching { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun runtimeExceptionBecomesRecoverableFailure() {
        val failure = IllegalStateException("binder unavailable")
        val result = runCatching<Int> { throw failure }

        assertFalse(result.isSuccess)
        assertSame(failure, result.exceptionOrNull())
        assertEquals(-1, result.getOrDefault(-1))
    }

    @Test(expected = AssertionError::class)
    fun fatalErrorPropagates() {
        runCatching<Int> { throw AssertionError("fatal") }
    }
}

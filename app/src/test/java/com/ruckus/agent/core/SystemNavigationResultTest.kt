package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SystemNavigationResultTest {
    @Test
    fun acceptedGlobalActionReturnsCanonicalActionName() {
        assertEquals("Back", SystemNavigationResult.requirePerformed("Back", true))
        assertEquals("Home", SystemNavigationResult.requirePerformed("Home", true))
    }

    @Test
    fun rejectedGlobalActionFailsClosed() {
        val error = assertThrows(IllegalStateException::class.java) {
            SystemNavigationResult.requirePerformed("Back", false)
        }
        assertEquals("Back global action was rejected by Accessibility", error.message)
    }

    @Test
    fun blankActionNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SystemNavigationResult.requirePerformed("   ", true)
        }
    }
}

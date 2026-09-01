package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafePreferenceReadTest {
    @Test
    fun `valid string read is preserved`() {
        assertEquals("checkpoint", SafePreferenceRead.stringOrNull { "checkpoint" })
    }

    @Test
    fun `missing preference stays null`() {
        assertNull(SafePreferenceRead.stringOrNull { null })
    }

    @Test
    fun `wrong stored type fails closed`() {
        assertNull(
            SafePreferenceRead.stringOrNull {
                throw ClassCastException("Preference active_task is not a String")
            }
        )
    }

    @Test
    fun `platform read failure fails closed`() {
        assertNull(
            SafePreferenceRead.stringOrNull {
                throw SecurityException("preferences unavailable")
            }
        )
    }
}

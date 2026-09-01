package com.ruckus.agent.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeStoredSettingsReadTest {
    @Test
    fun `valid persisted values are decoded normally`() {
        val loaded = SafeStoredSettingsRead.load {
            mapOf(
                StoredSettingsDecoder.KEY_ONBOARDING_COMPLETE to true,
                StoredSettingsDecoder.KEY_TELEMETRY_ENABLED to false,
                StoredSettingsDecoder.KEY_TUTORIAL_VERSION_SEEN to 4
            )
        }

        assertEquals(true, loaded.onboardingComplete)
        assertEquals(false, loaded.telemetryEnabled)
        assertEquals(4, loaded.tutorialVersionSeen)
    }

    @Test
    fun `runtime failure reading preference snapshot fails closed to defaults`() {
        val loaded = SafeStoredSettingsRead.load {
            throw IllegalStateException("preferences unavailable")
        }

        assertEquals(RukusSettings(), loaded)
    }

    @Test
    fun `fatal errors are not hidden as settings corruption`() {
        val fatal = AssertionError("fatal")

        try {
            SafeStoredSettingsRead.load { throw fatal }
            throw AssertionError("Expected fatal error to propagate")
        } catch (actual: AssertionError) {
            assertEquals(fatal, actual)
        }
    }
}

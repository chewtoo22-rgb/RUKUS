package com.ruckus.agent.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class StoredSettingsDecoderTest {
    @Test
    fun `decodes valid persisted settings`() {
        val decoded = StoredSettingsDecoder.decode(
            mapOf(
                StoredSettingsDecoder.KEY_ONBOARDING_COMPLETE to true,
                StoredSettingsDecoder.KEY_HAPTICS_ENABLED to false,
                StoredSettingsDecoder.KEY_TELEMETRY_ENABLED to false,
                StoredSettingsDecoder.KEY_CONFIRMATIONS_ENABLED to true,
                StoredSettingsDecoder.KEY_TUTORIAL_VERSION_SEEN to 3
            )
        )

        assertEquals(
            RukusSettings(
                onboardingComplete = true,
                hapticsEnabled = false,
                telemetryEnabled = false,
                confirmationsEnabled = true,
                tutorialVersionSeen = 3
            ),
            decoded
        )
    }

    @Test
    fun `malformed values fall back independently without crashing startup`() {
        val decoded = StoredSettingsDecoder.decode(
            mapOf(
                StoredSettingsDecoder.KEY_ONBOARDING_COMPLETE to "true",
                StoredSettingsDecoder.KEY_HAPTICS_ENABLED to 1,
                StoredSettingsDecoder.KEY_TELEMETRY_ENABLED to listOf(true),
                StoredSettingsDecoder.KEY_CONFIRMATIONS_ENABLED to "false",
                StoredSettingsDecoder.KEY_TUTORIAL_VERSION_SEEN to "99"
            )
        )

        assertEquals(RukusSettings(), decoded)
    }

    @Test
    fun `negative tutorial version is normalized while other valid fields survive`() {
        val decoded = StoredSettingsDecoder.decode(
            mapOf(
                StoredSettingsDecoder.KEY_ONBOARDING_COMPLETE to true,
                StoredSettingsDecoder.KEY_CONFIRMATIONS_ENABLED to false,
                StoredSettingsDecoder.KEY_TUTORIAL_VERSION_SEEN to -7
            )
        )

        assertEquals(true, decoded.onboardingComplete)
        assertEquals(false, decoded.confirmationsEnabled)
        assertEquals(0, decoded.tutorialVersionSeen)
    }
}

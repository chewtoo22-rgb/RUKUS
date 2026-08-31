package com.ruckus.agent.settings

internal object StoredSettingsDecoder {
    fun decode(values: Map<String, *>): RukusSettings {
        val defaults = RukusSettings()
        return RukusSettings(
            onboardingComplete = values[KEY_ONBOARDING_COMPLETE] as? Boolean
                ?: defaults.onboardingComplete,
            hapticsEnabled = values[KEY_HAPTICS_ENABLED] as? Boolean
                ?: defaults.hapticsEnabled,
            telemetryEnabled = values[KEY_TELEMETRY_ENABLED] as? Boolean
                ?: defaults.telemetryEnabled,
            confirmationsEnabled = values[KEY_CONFIRMATIONS_ENABLED] as? Boolean
                ?: defaults.confirmationsEnabled,
            tutorialVersionSeen = (values[KEY_TUTORIAL_VERSION_SEEN] as? Int)
                ?.coerceAtLeast(0)
                ?: defaults.tutorialVersionSeen
        )
    }

    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"
    const val KEY_CONFIRMATIONS_ENABLED = "confirmations_enabled"
    const val KEY_TUTORIAL_VERSION_SEEN = "tutorial_version_seen"
}

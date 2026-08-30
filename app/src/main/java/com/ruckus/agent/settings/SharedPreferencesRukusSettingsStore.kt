package com.ruckus.agent.settings

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesRukusSettingsStore(context: Context) : RukusSettingsStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): RukusSettings = RukusSettings(
        onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false),
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true),
        telemetryEnabled = prefs.getBoolean(KEY_TELEMETRY_ENABLED, true),
        confirmationsEnabled = prefs.getBoolean(KEY_CONFIRMATIONS_ENABLED, true),
        tutorialVersionSeen = prefs.getInt(KEY_TUTORIAL_VERSION_SEEN, 0).coerceAtLeast(0)
    )

    override fun save(settings: RukusSettings) {
        val committed = prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, settings.onboardingComplete)
            .putBoolean(KEY_HAPTICS_ENABLED, settings.hapticsEnabled)
            .putBoolean(KEY_TELEMETRY_ENABLED, settings.telemetryEnabled)
            .putBoolean(KEY_CONFIRMATIONS_ENABLED, settings.confirmationsEnabled)
            .putInt(KEY_TUTORIAL_VERSION_SEEN, settings.tutorialVersionSeen)
            .commit()

        check(committed) { "Failed to persist RUKUS settings" }
    }

    companion object {
        private const val PREFS_NAME = "rukus_settings_v1"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"
        private const val KEY_CONFIRMATIONS_ENABLED = "confirmations_enabled"
        private const val KEY_TUTORIAL_VERSION_SEEN = "tutorial_version_seen"
    }
}

package com.ruckus.agent.settings

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesRukusSettingsStore(context: Context) : RukusSettingsStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): RukusSettings = StoredSettingsDecoder.decode(prefs.all)

    override fun save(settings: RukusSettings) {
        val committed = prefs.edit()
            .putBoolean(StoredSettingsDecoder.KEY_ONBOARDING_COMPLETE, settings.onboardingComplete)
            .putBoolean(StoredSettingsDecoder.KEY_HAPTICS_ENABLED, settings.hapticsEnabled)
            .putBoolean(StoredSettingsDecoder.KEY_TELEMETRY_ENABLED, settings.telemetryEnabled)
            .putBoolean(StoredSettingsDecoder.KEY_CONFIRMATIONS_ENABLED, settings.confirmationsEnabled)
            .putInt(StoredSettingsDecoder.KEY_TUTORIAL_VERSION_SEEN, settings.tutorialVersionSeen)
            .commit()

        check(committed) { "Failed to persist RUKUS settings" }
    }

    companion object {
        private const val PREFS_NAME = "rukus_settings_v1"
    }
}

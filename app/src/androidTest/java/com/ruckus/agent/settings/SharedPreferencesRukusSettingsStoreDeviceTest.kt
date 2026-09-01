package com.ruckus.agent.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android SharedPreferences-backed settings store on device/emulator. */
@RunWith(AndroidJUnit4::class)
class SharedPreferencesRukusSettingsStoreDeviceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearStore()
    }

    @After
    fun tearDown() {
        clearStore()
    }

    @Test
    fun settingsRoundTripAcrossStoreInstances() {
        val expected = RukusSettings(
            onboardingComplete = true,
            hapticsEnabled = false,
            telemetryEnabled = false,
            confirmationsEnabled = false,
            tutorialVersionSeen = 7
        )

        SharedPreferencesRukusSettingsStore(context).save(expected)

        val reloaded = SharedPreferencesRukusSettingsStore(context).load()
        assertEquals(expected, reloaded)
    }

    @Test
    fun corruptedStoredTypesFailClosedToDefaults() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(StoredSettingsDecoder.KEY_ONBOARDING_COMPLETE, "not-a-boolean")
            .putString(StoredSettingsDecoder.KEY_TUTORIAL_VERSION_SEEN, "not-an-int")
            .commit()

        assertEquals(RukusSettings(), SharedPreferencesRukusSettingsStore(context).load())
    }

    private fun clearStore() {
        check(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        ) { "Failed to clear RUKUS settings test store" }
    }

    private companion object {
        const val PREFS_NAME = "rukus_settings_v1"
    }
}

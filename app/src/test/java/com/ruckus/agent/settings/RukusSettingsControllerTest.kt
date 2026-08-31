package com.ruckus.agent.settings

import com.ruckus.agent.core.ExecutionHealthTelemetry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RukusSettingsControllerTest {
    @After
    fun restoreTelemetryDefault() {
        ExecutionHealthTelemetry.setEnabled(true)
    }

    @Test
    fun freshInstallNeedsOnboarding() {
        val controller = RukusSettingsController(FakeStore(), currentTutorialVersion = 2)
        assertTrue(controller.needsOnboarding())
    }

    @Test
    fun controllerAppliesPersistedTelemetryPreferenceAtStartup() {
        RukusSettingsController(FakeStore(initial = RukusSettings(telemetryEnabled = false)))
        assertFalse(ExecutionHealthTelemetry.isEnabled())
    }

    @Test
    fun completingReadyOnboardingPersistsCurrentTutorialVersion() {
        val store = FakeStore()
        val controller = RukusSettingsController(store, currentTutorialVersion = 3)

        val plan = controller.completeOnboardingIfReady(
            OnboardingReadiness(
                accessibilityReady = true,
                writeSettingsReady = true,
                shizukuReady = true,
                safetyAcknowledged = true
            )
        )

        assertTrue(plan.canComplete)
        assertFalse(controller.needsOnboarding())
        assertTrue(controller.snapshot().onboardingComplete)
        assertEquals(3, controller.snapshot().tutorialVersionSeen)
        assertEquals(controller.snapshot(), store.persisted)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun readinessGateRefusesPersistenceWithoutAccessibility() {
        val store = FakeStore()
        val controller = RukusSettingsController(store, currentTutorialVersion = 2)

        val plan = controller.completeOnboardingIfReady(
            OnboardingReadiness(
                accessibilityReady = false,
                writeSettingsReady = true,
                shizukuReady = true,
                safetyAcknowledged = true
            )
        )

        assertFalse(plan.canComplete)
        assertEquals(OnboardingStep.ACCESSIBILITY, plan.currentStep)
        assertTrue(controller.needsOnboarding())
        assertFalse(store.persisted.onboardingComplete)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun readinessGateRefusesPersistenceWithoutSafetyAcknowledgement() {
        val store = FakeStore()
        val controller = RukusSettingsController(store)

        val plan = controller.completeOnboardingIfReady(
            OnboardingReadiness(
                accessibilityReady = true,
                writeSettingsReady = true,
                shizukuReady = true,
                safetyAcknowledged = false
            )
        )

        assertFalse(plan.canComplete)
        assertEquals(OnboardingStep.SAFETY, plan.currentStep)
        assertTrue(controller.needsOnboarding())
        assertEquals(0, store.saveCount)
    }

    @Test
    fun optionalCapabilitiesDoNotStrandSupportedCoreCommands() {
        val store = FakeStore()
        val controller = RukusSettingsController(store, currentTutorialVersion = 4)

        val plan = controller.completeOnboardingIfReady(
            OnboardingReadiness(
                accessibilityReady = true,
                writeSettingsReady = false,
                shizukuReady = false,
                safetyAcknowledged = true
            )
        )

        assertTrue(plan.canComplete)
        assertEquals(listOf(OnboardingStep.WRITE_SETTINGS, OnboardingStep.SHIZUKU), plan.optionalSetup)
        assertFalse(controller.needsOnboarding())
        assertTrue(store.persisted.onboardingComplete)
        assertEquals(4, store.persisted.tutorialVersionSeen)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun settingChangesPersistBeforeBecomingVisible() {
        val store = FakeStore()
        val controller = RukusSettingsController(store)
        controller.setTelemetryEnabled(false)
        controller.setHapticsEnabled(false)
        controller.setConfirmationsEnabled(false)
        assertFalse(controller.snapshot().telemetryEnabled)
        assertFalse(controller.snapshot().hapticsEnabled)
        assertFalse(controller.snapshot().confirmationsEnabled)
        assertFalse(ExecutionHealthTelemetry.isEnabled())
        assertEquals(controller.snapshot(), store.persisted)
    }

    @Test
    fun failedPersistenceDoesNotAdvanceInMemoryOrRuntimeTelemetryState() {
        val store = FakeStore(failWrites = true)
        val controller = RukusSettingsController(store)
        val before = controller.snapshot()
        assertTrue(ExecutionHealthTelemetry.isEnabled())

        runCatching { controller.setTelemetryEnabled(false) }

        assertEquals(before, controller.snapshot())
        assertEquals(before, store.persisted)
        assertTrue(ExecutionHealthTelemetry.isEnabled())
    }

    @Test
    fun noOpUpdateDoesNotWrite() {
        val store = FakeStore()
        val controller = RukusSettingsController(store)
        controller.setTelemetryEnabled(true)
        assertEquals(0, store.saveCount)
        assertTrue(ExecutionHealthTelemetry.isEnabled())
    }

    @Test
    fun newerTutorialVersionReopensOnboarding() {
        val store = FakeStore(initial = RukusSettings(onboardingComplete = true, tutorialVersionSeen = 1))
        val controller = RukusSettingsController(store, currentTutorialVersion = 2)
        assertTrue(controller.needsOnboarding())
    }

    private class FakeStore(
        initial: RukusSettings = RukusSettings(),
        private val failWrites: Boolean = false
    ) : RukusSettingsStore {
        var persisted: RukusSettings = initial
            private set
        var saveCount: Int = 0
            private set

        override fun load(): RukusSettings = persisted

        override fun save(settings: RukusSettings) {
            saveCount++
            if (failWrites) error("simulated storage failure")
            persisted = settings
        }
    }
}

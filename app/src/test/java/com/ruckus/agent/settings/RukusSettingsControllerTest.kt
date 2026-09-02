package com.ruckus.agent.settings

import com.ruckus.agent.core.ConfirmationRuntimePolicy
import com.ruckus.agent.core.ExecutionHealthTelemetry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RukusSettingsControllerTest {
    @After
    fun restoreRuntimeDefaults() {
        ExecutionHealthTelemetry.setEnabled(true)
        ConfirmationRuntimePolicy.setPromptsEnabled(true)
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
    fun controllerAppliesPersistedConfirmationPreferenceAtStartup() {
        RukusSettingsController(FakeStore(initial = RukusSettings(confirmationsEnabled = false)))
        assertFalse(ConfirmationRuntimePolicy.promptsEnabled())
    }

    @Test
    fun completingReadyOnboardingPersistsCurrentTutorialVersion() {
        val store = FakeStore()
        val controller = RukusSettingsController(store, currentTutorialVersion = 3)
        val plan = controller.completeOnboardingIfReady(ready())

        assertTrue(plan.canComplete)
        assertFalse(controller.needsOnboarding())
        assertTrue(controller.snapshot().onboardingComplete)
        assertEquals(3, controller.snapshot().tutorialVersionSeen)
        assertEquals(controller.snapshot(), store.persisted)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun onboardingPersistenceFailureStaysIncompleteAndReturnsBlocker() {
        val store = FakeStore(failWrites = true)
        val controller = RukusSettingsController(store, currentTutorialVersion = 3)
        val before = controller.snapshot()

        val plan = controller.completeOnboardingIfReady(ready())

        assertFalse(plan.canComplete)
        assertEquals(OnboardingStep.PERSISTENCE, plan.currentStep)
        assertEquals(listOf(OnboardingStep.PERSISTENCE), plan.requiredBlockers)
        assertTrue(controller.needsOnboarding())
        assertEquals(before, controller.snapshot())
        assertEquals(before, store.persisted)
        assertEquals(1, store.saveCount)
    }

    @Test(expected = AssertionError::class)
    fun fatalOnboardingPersistenceErrorStillPropagates() {
        val controller = RukusSettingsController(FatalStore())
        controller.completeOnboardingIfReady(ready())
    }

    @Test
    fun readinessGateRefusesPersistenceWithoutAccessibility() {
        val store = FakeStore()
        val controller = RukusSettingsController(store, currentTutorialVersion = 2)
        val plan = controller.completeOnboardingIfReady(
            ready().copy(accessibilityReady = false)
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
            ready().copy(safetyAcknowledged = false)
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
            ready().copy(writeSettingsReady = false, shizukuReady = false)
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
        assertFalse(ConfirmationRuntimePolicy.promptsEnabled())
        assertEquals(controller.snapshot(), store.persisted)
    }

    @Test
    fun failedTelemetryPersistenceReturnsPreviousStateWithoutChangingRuntime() {
        val store = FakeStore(failWrites = true)
        val controller = RukusSettingsController(store)
        val before = controller.snapshot()
        assertTrue(ExecutionHealthTelemetry.isEnabled())

        val returned = controller.setTelemetryEnabled(false)

        assertEquals(before, returned)
        assertEquals(before, controller.snapshot())
        assertEquals(before, store.persisted)
        assertTrue(ExecutionHealthTelemetry.isEnabled())
    }

    @Test
    fun failedConfirmationPersistenceReturnsPreviousStateWithoutChangingRuntime() {
        val store = FakeStore(failWrites = true)
        val controller = RukusSettingsController(store)
        val before = controller.snapshot()
        assertTrue(ConfirmationRuntimePolicy.promptsEnabled())

        val returned = controller.setConfirmationsEnabled(false)

        assertEquals(before, returned)
        assertEquals(before, controller.snapshot())
        assertEquals(before, store.persisted)
        assertTrue(ConfirmationRuntimePolicy.promptsEnabled())
    }

    @Test
    fun failedHapticsPersistenceReturnsPreviousState() {
        val store = FakeStore(failWrites = true)
        val controller = RukusSettingsController(store)
        val before = controller.snapshot()

        val returned = controller.setHapticsEnabled(false)

        assertEquals(before, returned)
        assertEquals(before, controller.snapshot())
        assertEquals(before, store.persisted)
    }

    @Test(expected = AssertionError::class)
    fun fatalSettingsPersistenceErrorStillPropagates() {
        val controller = RukusSettingsController(FatalStore())
        controller.setHapticsEnabled(false)
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

    private fun ready() = OnboardingReadiness(
        accessibilityReady = true,
        writeSettingsReady = true,
        shizukuReady = true,
        safetyAcknowledged = true
    )

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

    private class FatalStore : RukusSettingsStore {
        override fun load(): RukusSettings = RukusSettings()
        override fun save(settings: RukusSettings) {
            throw AssertionError("fatal simulated failure")
        }
    }
}

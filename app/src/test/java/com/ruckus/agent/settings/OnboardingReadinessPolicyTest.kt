package com.ruckus.agent.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingReadinessPolicyTest {
    @Test
    fun `required safety step wins over optional capability setup`() {
        val plan = OnboardingReadinessPolicy.evaluate(
            OnboardingReadiness(
                accessibilityReady = true,
                writeSettingsReady = false,
                shizukuReady = false,
                safetyAcknowledged = false
            )
        )

        assertEquals(OnboardingStep.SAFETY, plan.currentStep)
        assertEquals(listOf(OnboardingStep.SAFETY), plan.requiredBlockers)
        assertEquals(
            listOf(OnboardingStep.WRITE_SETTINGS, OnboardingStep.SHIZUKU),
            plan.optionalSetup
        )
        assertFalse(plan.canComplete)
    }

    @Test
    fun `accessibility remains first required blocker`() {
        val plan = OnboardingReadinessPolicy.evaluate(
            OnboardingReadiness(
                accessibilityReady = false,
                writeSettingsReady = false,
                shizukuReady = false,
                safetyAcknowledged = false
            )
        )

        assertEquals(OnboardingStep.ACCESSIBILITY, plan.currentStep)
        assertEquals(
            listOf(OnboardingStep.ACCESSIBILITY, OnboardingStep.SAFETY),
            plan.requiredBlockers
        )
        assertFalse(plan.canComplete)
    }

    @Test
    fun `optional setup is surfaced only after required blockers clear`() {
        val plan = OnboardingReadinessPolicy.evaluate(
            OnboardingReadiness(
                accessibilityReady = true,
                writeSettingsReady = false,
                shizukuReady = false,
                safetyAcknowledged = true
            )
        )

        assertEquals(OnboardingStep.WRITE_SETTINGS, plan.currentStep)
        assertTrue(plan.requiredBlockers.isEmpty())
        assertTrue(plan.canComplete)
    }

    @Test
    fun `fully ready onboarding reaches ready`() {
        val plan = OnboardingReadinessPolicy.evaluate(
            OnboardingReadiness(
                accessibilityReady = true,
                writeSettingsReady = true,
                shizukuReady = true,
                safetyAcknowledged = true
            )
        )

        assertEquals(OnboardingStep.READY, plan.currentStep)
        assertTrue(plan.requiredBlockers.isEmpty())
        assertTrue(plan.optionalSetup.isEmpty())
        assertTrue(plan.canComplete)
    }
}

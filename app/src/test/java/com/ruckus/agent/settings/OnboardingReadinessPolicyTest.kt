package com.ruckus.agent.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingReadinessPolicyTest {
    @Test
    fun introIsTheFirstGateWhenNotSeen() {
        val plan = OnboardingReadinessPolicy.evaluate(
            readiness = ready(),
            introSeen = false
        )

        assertEquals(OnboardingStep.INTRO, plan.currentStep)
        assertEquals(listOf(OnboardingStep.INTRO), plan.requiredBlockers)
        assertFalse(plan.canComplete)
    }

    @Test
    fun accessibilityBlocksCompletion() {
        val plan = OnboardingReadinessPolicy.evaluate(
            ready().copy(accessibilityReady = false)
        )

        assertEquals(OnboardingStep.ACCESSIBILITY, plan.currentStep)
        assertTrue(OnboardingStep.ACCESSIBILITY in plan.requiredBlockers)
        assertFalse(plan.canComplete)
    }

    @Test
    fun writeSettingsIsVisibleButOptional() {
        val plan = OnboardingReadinessPolicy.evaluate(
            ready().copy(writeSettingsReady = false)
        )

        assertEquals(OnboardingStep.WRITE_SETTINGS, plan.currentStep)
        assertEquals(listOf(OnboardingStep.WRITE_SETTINGS), plan.optionalSetup)
        assertTrue(plan.requiredBlockers.isEmpty())
        assertTrue(plan.canComplete)
    }

    @Test
    fun shizukuIsVisibleButOptional() {
        val plan = OnboardingReadinessPolicy.evaluate(
            ready().copy(shizukuReady = false)
        )

        assertEquals(OnboardingStep.SHIZUKU, plan.currentStep)
        assertEquals(listOf(OnboardingStep.SHIZUKU), plan.optionalSetup)
        assertTrue(plan.canComplete)
    }

    @Test
    fun safetyAcknowledgementBlocksCompletionAfterCapabilitySetup() {
        val plan = OnboardingReadinessPolicy.evaluate(
            ready().copy(safetyAcknowledged = false)
        )

        assertEquals(OnboardingStep.SAFETY, plan.currentStep)
        assertEquals(listOf(OnboardingStep.SAFETY), plan.requiredBlockers)
        assertFalse(plan.canComplete)
    }

    @Test
    fun multipleMissingCapabilitiesStayDeterministic() {
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
        assertEquals(
            listOf(OnboardingStep.WRITE_SETTINGS, OnboardingStep.SHIZUKU),
            plan.optionalSetup
        )
        assertFalse(plan.canComplete)
    }

    @Test
    fun fullyReadyStateCanComplete() {
        val plan = OnboardingReadinessPolicy.evaluate(ready())

        assertEquals(OnboardingStep.READY, plan.currentStep)
        assertTrue(plan.requiredBlockers.isEmpty())
        assertTrue(plan.optionalSetup.isEmpty())
        assertTrue(plan.canComplete)
    }

    private fun ready() = OnboardingReadiness(
        accessibilityReady = true,
        writeSettingsReady = true,
        shizukuReady = true,
        safetyAcknowledged = true
    )
}

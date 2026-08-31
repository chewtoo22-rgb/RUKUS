package com.ruckus.agent.settings

/**
 * Android-independent first-run readiness snapshot.
 *
 * UI surfaces map live Android capability state into this contract so persistence cannot mark
 * onboarding complete before release-critical prerequisites are actually satisfied.
 */
data class OnboardingReadiness(
    val accessibilityReady: Boolean,
    val writeSettingsReady: Boolean,
    val shizukuReady: Boolean,
    val safetyAcknowledged: Boolean
)

enum class OnboardingStep {
    INTRO,
    ACCESSIBILITY,
    WRITE_SETTINGS,
    SHIZUKU,
    SAFETY,
    READY
}

data class OnboardingPlan(
    val currentStep: OnboardingStep,
    val requiredBlockers: List<OnboardingStep>,
    val optionalSetup: List<OnboardingStep>,
    val canComplete: Boolean
)

object OnboardingReadinessPolicy {
    fun evaluate(readiness: OnboardingReadiness, introSeen: Boolean = true): OnboardingPlan {
        if (!introSeen) {
            return OnboardingPlan(
                currentStep = OnboardingStep.INTRO,
                requiredBlockers = listOf(OnboardingStep.INTRO),
                optionalSetup = optionalSteps(readiness),
                canComplete = false
            )
        }

        val required = buildList {
            if (!readiness.accessibilityReady) add(OnboardingStep.ACCESSIBILITY)
            if (!readiness.safetyAcknowledged) add(OnboardingStep.SAFETY)
        }
        val optional = optionalSteps(readiness)
        val current = when {
            required.isNotEmpty() -> required.first()
            optional.isNotEmpty() -> optional.first()
            else -> OnboardingStep.READY
        }

        return OnboardingPlan(
            currentStep = current,
            requiredBlockers = required,
            optionalSetup = optional,
            canComplete = required.isEmpty()
        )
    }

    private fun optionalSteps(readiness: OnboardingReadiness): List<OnboardingStep> = buildList {
        if (!readiness.writeSettingsReady) add(OnboardingStep.WRITE_SETTINGS)
        if (!readiness.shizukuReady) add(OnboardingStep.SHIZUKU)
    }
}

package com.ruckus.agent.settings

/**
 * Pure, Android-independent onboarding readiness contract.
 *
 * Android surfaces can map their live permission/capability snapshot into this type without
 * letting UI state decide whether onboarding is actually safe to complete.
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

/**
 * Accessibility and explicit safety acknowledgement are release-critical onboarding gates.
 * WRITE_SETTINGS and Shizuku are capability enhancers: they are surfaced during onboarding but
 * do not strand users whose device/ROM cannot provide them. Individual commands still fail closed
 * later when a required capability is unavailable.
 */
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
            !readiness.accessibilityReady -> OnboardingStep.ACCESSIBILITY
            !readiness.writeSettingsReady -> OnboardingStep.WRITE_SETTINGS
            !readiness.shizukuReady -> OnboardingStep.SHIZUKU
            !readiness.safetyAcknowledged -> OnboardingStep.SAFETY
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

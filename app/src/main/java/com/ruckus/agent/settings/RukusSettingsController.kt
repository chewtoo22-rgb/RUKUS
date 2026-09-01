package com.ruckus.agent.settings

import com.ruckus.agent.core.ConfirmationRuntimePolicy
import com.ruckus.agent.core.ExecutionHealthTelemetry

class RukusSettingsController(
    private val store: RukusSettingsStore,
    private val currentTutorialVersion: Int = 1
) {
    init {
        require(currentTutorialVersion >= 0) { "currentTutorialVersion must be non-negative" }
    }

    private var current: RukusSettings = store.load().also {
        ExecutionHealthTelemetry.setEnabled(it.telemetryEnabled)
        ConfirmationRuntimePolicy.setPromptsEnabled(it.confirmationsEnabled)
    }

    fun snapshot(): RukusSettings = current

    fun needsOnboarding(): Boolean = current.needsOnboarding(currentTutorialVersion)

    fun setTelemetryEnabled(enabled: Boolean): RukusSettings {
        val next = update { it.copy(telemetryEnabled = enabled) }
        ExecutionHealthTelemetry.setEnabled(next.telemetryEnabled)
        return next
    }

    fun setHapticsEnabled(enabled: Boolean): RukusSettings = update {
        it.copy(hapticsEnabled = enabled)
    }

    fun setConfirmationsEnabled(enabled: Boolean): RukusSettings {
        val next = update { it.copy(confirmationsEnabled = enabled) }
        ConfirmationRuntimePolicy.setPromptsEnabled(next.confirmationsEnabled)
        return next
    }

    /**
     * Completes onboarding only after the release-critical readiness contract passes.
     * Optional capabilities remain visible in the returned plan but do not strand unsupported
     * devices; individual commands still fail closed when those capabilities are required.
     *
     * A durable-storage failure is reported as a persistence blocker instead of escaping into the
     * Android click handler. The in-memory state remains unchanged because update() only publishes
     * after save() succeeds.
     */
    fun completeOnboardingIfReady(
        readiness: OnboardingReadiness,
        introSeen: Boolean = true
    ): OnboardingPlan {
        val plan = OnboardingReadinessPolicy.evaluate(readiness, introSeen)
        if (!plan.canComplete) return plan

        return try {
            update { it.completeOnboarding(currentTutorialVersion) }
            plan
        } catch (_: RuntimeException) {
            OnboardingPlan(
                currentStep = OnboardingStep.PERSISTENCE,
                requiredBlockers = listOf(OnboardingStep.PERSISTENCE),
                optionalSetup = plan.optionalSetup,
                canComplete = false
            )
        }
    }

    private inline fun update(transform: (RukusSettings) -> RukusSettings): RukusSettings {
        val next = transform(current)
        if (next == current) return current

        store.save(next)
        current = next
        return current
    }
}

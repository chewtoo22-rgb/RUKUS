package com.ruckus.agent.settings

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

    fun setConfirmationsEnabled(enabled: Boolean): RukusSettings = update {
        it.copy(confirmationsEnabled = enabled)
    }

    /**
     * Completes onboarding only after the release-critical readiness contract passes.
     * Optional capabilities remain visible in the returned plan but do not strand unsupported
     * devices; individual commands still fail closed when those capabilities are required.
     */
    fun completeOnboardingIfReady(
        readiness: OnboardingReadiness,
        introSeen: Boolean = true
    ): OnboardingPlan {
        val plan = OnboardingReadinessPolicy.evaluate(readiness, introSeen)
        if (plan.canComplete) {
            update { it.completeOnboarding(currentTutorialVersion) }
        }
        return plan
    }

    /** Compatibility path for callers that already enforce readiness externally. */
    fun completeOnboarding(): RukusSettings = update {
        it.completeOnboarding(currentTutorialVersion)
    }

    private inline fun update(transform: (RukusSettings) -> RukusSettings): RukusSettings {
        val next = transform(current)
        if (next == current) return current

        store.save(next)
        current = next
        return current
    }
}

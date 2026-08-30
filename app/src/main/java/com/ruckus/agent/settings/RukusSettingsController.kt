package com.ruckus.agent.settings

class RukusSettingsController(
    private val store: RukusSettingsStore,
    private val currentTutorialVersion: Int = 1
) {
    init {
        require(currentTutorialVersion >= 0) { "currentTutorialVersion must be non-negative" }
    }

    private var current: RukusSettings = store.load()

    fun snapshot(): RukusSettings = current

    fun needsOnboarding(): Boolean = current.needsOnboarding(currentTutorialVersion)

    fun setTelemetryEnabled(enabled: Boolean): RukusSettings = update {
        it.copy(telemetryEnabled = enabled)
    }

    fun setHapticsEnabled(enabled: Boolean): RukusSettings = update {
        it.copy(hapticsEnabled = enabled)
    }

    fun setConfirmationsEnabled(enabled: Boolean): RukusSettings = update {
        it.copy(confirmationsEnabled = enabled)
    }

    /**
     * Transactionally completes onboarding only when the release-critical readiness gates pass.
     * Optional capabilities remain visible in the returned plan but do not prevent completion.
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

    /**
     * Compatibility path for callers that already performed readiness validation externally.
     * New first-run surfaces should use completeOnboardingIfReady().
     */
    fun completeOnboarding(): RukusSettings = update {
        it.completeOnboarding(currentTutorialVersion)
    }

    private inline fun update(transform: (RukusSettings) -> RukusSettings): RukusSettings {
        val next = transform(current)
        if (next == current) return current

        // Persist first. If storage fails, the controller keeps the last known durable state.
        store.save(next)
        current = next
        return current
    }
}

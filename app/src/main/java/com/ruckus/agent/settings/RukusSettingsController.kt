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

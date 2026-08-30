package com.ruckus.agent.settings

data class RukusSettings(
    val onboardingComplete: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val confirmationsEnabled: Boolean = true,
    val tutorialVersionSeen: Int = 0
) {
    init {
        require(tutorialVersionSeen >= 0) { "tutorialVersionSeen must be non-negative" }
    }

    fun needsOnboarding(currentTutorialVersion: Int): Boolean {
        require(currentTutorialVersion >= 0) { "currentTutorialVersion must be non-negative" }
        return !onboardingComplete || tutorialVersionSeen < currentTutorialVersion
    }

    fun completeOnboarding(currentTutorialVersion: Int): RukusSettings {
        require(currentTutorialVersion >= 0) { "currentTutorialVersion must be non-negative" }
        return copy(
            onboardingComplete = true,
            tutorialVersionSeen = currentTutorialVersion
        )
    }
}

interface RukusSettingsStore {
    fun load(): RukusSettings
    fun save(settings: RukusSettings)
}

package com.ruckus.agent.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RukusSettingsTest {
    @Test
    fun freshInstallNeedsOnboarding() {
        assertTrue(RukusSettings().needsOnboarding(currentTutorialVersion = 1))
    }

    @Test
    fun completedCurrentTutorialDoesNotNeedOnboarding() {
        val settings = RukusSettings().completeOnboarding(currentTutorialVersion = 2)
        assertFalse(settings.needsOnboarding(currentTutorialVersion = 2))
        assertTrue(settings.onboardingComplete)
        assertEquals(2, settings.tutorialVersionSeen)
    }

    @Test
    fun newerTutorialVersionReopensOnboarding() {
        val settings = RukusSettings(
            onboardingComplete = true,
            tutorialVersionSeen = 1
        )
        assertTrue(settings.needsOnboarding(currentTutorialVersion = 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeStoredTutorialVersionIsRejected() {
        RukusSettings(tutorialVersionSeen = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCurrentTutorialVersionIsRejected() {
        RukusSettings().needsOnboarding(currentTutorialVersion = -1)
    }
}

package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator probe for bounded adaptive recovery admission.
 *
 * CI compiles this into the instrumentation APK. Execution remains a Thursday
 * device/emulator test so no physical-runtime result is implied by compilation.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveRecoveryDeviceTest {

    @Test
    fun uniqueVisibleLabelMayProduceEquivalentSafeTapAlternate() {
        val original = AgentAction.TapLabel("Settings")
        val recovery = AdaptiveRecoveryPlanner.replan(
            action = original,
            screen = "pkg=com.example | labels=Setting • Search | screen=Example",
            failure = "Target not found"
        )

        val alternate = recovery.alternate
        assertNotNull(alternate)
        alternate!!
        assertTrue(SafetyGate.classify(alternate).risk == Risk.SAFE)
        assertTrue(RecoveryEquivalence.canSubstitute(original, alternate, recovery.confidence))
    }

    @Test
    fun ambiguousVisibleLabelsFailClosedInsteadOfGuessing() {
        val recovery = AdaptiveRecoveryPlanner.replan(
            action = AgentAction.TapLabel("Settings"),
            screen = "pkg=com.example | labels=Setting • Settings app | screen=Example",
            failure = "Target not found"
        )

        assertNull(recovery.alternate)
    }

    @Test
    fun safeButNonEquivalentFallbackCannotSatisfyOriginalStep() {
        val original = AgentAction.Back
        val recovery = AdaptiveRecoveryPlanner.replan(
            action = original,
            screen = "pkg=com.example | labels=Home | screen=Example",
            failure = "Back failed"
        )

        val alternate = recovery.alternate
        assertNotNull(alternate)
        alternate!!
        assertTrue(SafetyGate.classify(alternate).risk == Risk.SAFE)
        assertFalse(RecoveryEquivalence.canSubstitute(original, alternate, recovery.confidence))
    }

    @Test
    fun semanticScrollFallbackMustPreserveDirectionAndConsumeBoundedRecovery() {
        val original = AgentAction.Scroll(AgentAction.Direction.DOWN)
        val recovery = AdaptiveRecoveryPlanner.replan(
            action = original,
            screen = "pkg=com.example | screen=List",
            failure = "Semantic scroll failed"
        )

        val alternate = recovery.alternate
        assertNotNull(alternate)
        alternate!!
        assertTrue(SafetyGate.classify(alternate).risk == Risk.SAFE)
        assertTrue(RecoveryEquivalence.canSubstitute(original, alternate, recovery.confidence))

        val first = RecoveryBudget.decide(0)
        assertTrue(first.allowed)
        val exhausted = RecoveryBudget.decide(RecoveryBudget.MAX_TOTAL_ATTEMPTS)
        assertFalse(exhausted.allowed)
    }
}

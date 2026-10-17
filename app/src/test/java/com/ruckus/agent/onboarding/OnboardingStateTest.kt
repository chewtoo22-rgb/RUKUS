package com.ruckus.agent.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun nextWalksTutorialInOrderWithoutAutoCompleting() {
        var state = OnboardingState()
        state = OnboardingReducer.reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.Capabilities, state.step)
        state = OnboardingReducer.reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.Permissions, state.step)
        state = OnboardingReducer.reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.Safety, state.step)
        state = OnboardingReducer.reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.TryCommand, state.step)
        state = OnboardingReducer.reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.Complete, state.step)
        assertFalse(state.completed)
    }

    @Test
    fun finishIsExplicitAndTerminal() {
        val tryCommand = OnboardingState(step = OnboardingStep.TryCommand)
        val completed = OnboardingReducer.reduce(tryCommand, OnboardingEvent.Finish)
        assertTrue(completed.completed)
        assertEquals(OnboardingStep.Complete, completed.step)
        assertEquals(completed, OnboardingReducer.reduce(completed, OnboardingEvent.Back))
    }

    @Test(expected = IllegalArgumentException::class)
    fun finishFailsClosedBeforeTryCommand() {
        OnboardingReducer.reduce(OnboardingState(step = OnboardingStep.Safety), OnboardingEvent.Finish)
    }

    @Test
    fun backNeverEscapesWelcome() {
        val state = OnboardingReducer.reduce(OnboardingState(), OnboardingEvent.Back)
        assertEquals(OnboardingStep.Welcome, state.step)
    }

    @Test
    fun skipIsDistinctFromCompletionAndTerminal() {
        val skipped = OnboardingReducer.reduce(
            OnboardingState(step = OnboardingStep.Permissions),
            OnboardingEvent.Skip
        )
        assertTrue(skipped.skipped)
        assertFalse(skipped.completed)
        assertEquals(OnboardingStep.Complete, skipped.step)
        assertEquals(skipped, OnboardingReducer.reduce(skipped, OnboardingEvent.Next))
    }

    @Test
    fun restartClearsCompletedState() {
        val completed = OnboardingState(step = OnboardingStep.Complete, completed = true)
        assertEquals(OnboardingState(), OnboardingReducer.reduce(completed, OnboardingEvent.Restart))
    }

    @Test
    fun restartClearsSkippedState() {
        val skipped = OnboardingState(step = OnboardingStep.Complete, skipped = true)
        assertEquals(OnboardingState(), OnboardingReducer.reduce(skipped, OnboardingEvent.Restart))
    }

    @Test(expected = IllegalArgumentException::class)
    fun stateRejectsCompletedAndSkippedTogether() {
        OnboardingState(step = OnboardingStep.Complete, completed = true, skipped = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun completedStateMustPointAtCompleteStep() {
        OnboardingState(step = OnboardingStep.Safety, completed = true)
    }
}

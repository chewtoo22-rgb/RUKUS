package com.ruckus.agent.onboarding

enum class OnboardingStep {
    Welcome,
    Capabilities,
    Permissions,
    Safety,
    TryCommand,
    Complete
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val completed: Boolean = false,
    val skipped: Boolean = false
) {
    init {
        require(!(completed && skipped)) { "onboarding cannot be both completed and skipped" }
        if (completed) require(step == OnboardingStep.Complete) { "completed onboarding must be at Complete" }
    }
}

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
    data object Skip : OnboardingEvent
    data object Restart : OnboardingEvent
    data object Finish : OnboardingEvent
}

object OnboardingReducer {
    private val orderedSteps = listOf(
        OnboardingStep.Welcome,
        OnboardingStep.Capabilities,
        OnboardingStep.Permissions,
        OnboardingStep.Safety,
        OnboardingStep.TryCommand,
        OnboardingStep.Complete
    )

    fun reduce(state: OnboardingState, event: OnboardingEvent): OnboardingState {
        if (state.completed && event != OnboardingEvent.Restart) return state
        if (state.skipped && event != OnboardingEvent.Restart) return state

        return when (event) {
            OnboardingEvent.Restart -> OnboardingState()
            OnboardingEvent.Skip -> OnboardingState(
                step = OnboardingStep.Complete,
                completed = false,
                skipped = true
            )
            OnboardingEvent.Finish -> {
                require(state.step == OnboardingStep.TryCommand || state.step == OnboardingStep.Complete) {
                    "onboarding cannot finish before the TryCommand step"
                }
                OnboardingState(step = OnboardingStep.Complete, completed = true)
            }
            OnboardingEvent.Next -> move(state, +1)
            OnboardingEvent.Back -> move(state, -1)
        }
    }

    private fun move(state: OnboardingState, delta: Int): OnboardingState {
        val currentIndex = orderedSteps.indexOf(state.step)
        require(currentIndex >= 0) { "unknown onboarding step" }

        val targetIndex = (currentIndex + delta).coerceIn(0, orderedSteps.lastIndex)
        val target = orderedSteps[targetIndex]

        // Reaching the final screen does not silently mark onboarding complete.
        // Completion is an explicit event so persistence/UI layers cannot confuse
        // viewing the last page with acknowledging the tutorial.
        return state.copy(step = target)
    }
}

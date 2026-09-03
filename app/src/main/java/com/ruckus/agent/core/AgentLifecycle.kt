package com.ruckus.agent.core

/**
 * Pure, side-effect-free lifecycle contract for a single RUKUS task.
 *
 * The reducer deliberately knows nothing about Android, UI state, command parsing,
 * telemetry, or device execution. Callers must explicitly drive every transition.
 * Illegal transitions fail closed instead of silently skipping lifecycle stages.
 */
enum class AgentLifecycleState {
    IDLE,
    UNDERSTANDING,
    PLANNING,
    AWAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    RECOVERING,
    CANCELLING,
    SUCCEEDED,
    CANCELLED,
    BLOCKED,
    FAILED;

    val isTerminal: Boolean
        get() = this == SUCCEEDED || this == CANCELLED || this == BLOCKED || this == FAILED

    val isActive: Boolean
        get() = this != IDLE && !isTerminal
}

enum class AgentLifecycleEvent {
    REQUEST_ACCEPTED,
    UNDERSTANDING_COMPLETE,
    PLAN_READY,
    CONFIRMATION_REQUIRED,
    CONFIRMED,
    PREFLIGHT_BLOCKED,
    ACTIONS_COMPLETE,
    VERIFICATION_PASSED,
    VERIFICATION_FAILED,
    RECOVERY_READY,
    RECOVERY_EXHAUSTED,
    CANCEL_REQUESTED,
    CANCELLATION_COMPLETE,
    CANCELLATION_FAILED,
    TERMINAL_ACKNOWLEDGED,
}

class IllegalAgentLifecycleTransition(
    val state: AgentLifecycleState,
    val event: AgentLifecycleEvent,
) : IllegalStateException("illegal RUKUS lifecycle transition: $state + $event")

object AgentLifecycleReducer {
    fun transition(
        state: AgentLifecycleState,
        event: AgentLifecycleEvent,
    ): AgentLifecycleState = when (state) {
        AgentLifecycleState.IDLE -> when (event) {
            AgentLifecycleEvent.REQUEST_ACCEPTED -> AgentLifecycleState.UNDERSTANDING
            else -> illegal(state, event)
        }

        AgentLifecycleState.UNDERSTANDING -> when (event) {
            AgentLifecycleEvent.UNDERSTANDING_COMPLETE -> AgentLifecycleState.PLANNING
            AgentLifecycleEvent.CANCEL_REQUESTED -> AgentLifecycleState.CANCELLED
            else -> illegal(state, event)
        }

        AgentLifecycleState.PLANNING -> when (event) {
            AgentLifecycleEvent.PLAN_READY -> AgentLifecycleState.EXECUTING
            AgentLifecycleEvent.CONFIRMATION_REQUIRED -> AgentLifecycleState.AWAITING_CONFIRMATION
            AgentLifecycleEvent.PREFLIGHT_BLOCKED -> AgentLifecycleState.BLOCKED
            AgentLifecycleEvent.CANCEL_REQUESTED -> AgentLifecycleState.CANCELLED
            else -> illegal(state, event)
        }

        AgentLifecycleState.AWAITING_CONFIRMATION -> when (event) {
            AgentLifecycleEvent.CONFIRMED -> AgentLifecycleState.EXECUTING
            AgentLifecycleEvent.PREFLIGHT_BLOCKED -> AgentLifecycleState.BLOCKED
            AgentLifecycleEvent.CANCEL_REQUESTED -> AgentLifecycleState.CANCELLED
            else -> illegal(state, event)
        }

        AgentLifecycleState.EXECUTING -> when (event) {
            AgentLifecycleEvent.ACTIONS_COMPLETE -> AgentLifecycleState.VERIFYING
            AgentLifecycleEvent.PREFLIGHT_BLOCKED -> AgentLifecycleState.BLOCKED
            AgentLifecycleEvent.CANCEL_REQUESTED -> AgentLifecycleState.CANCELLING
            else -> illegal(state, event)
        }

        AgentLifecycleState.VERIFYING -> when (event) {
            AgentLifecycleEvent.VERIFICATION_PASSED -> AgentLifecycleState.SUCCEEDED
            AgentLifecycleEvent.VERIFICATION_FAILED -> AgentLifecycleState.RECOVERING
            AgentLifecycleEvent.CANCEL_REQUESTED -> AgentLifecycleState.CANCELLING
            else -> illegal(state, event)
        }

        AgentLifecycleState.RECOVERING -> when (event) {
            AgentLifecycleEvent.RECOVERY_READY -> AgentLifecycleState.EXECUTING
            AgentLifecycleEvent.RECOVERY_EXHAUSTED -> AgentLifecycleState.FAILED
            AgentLifecycleEvent.CANCEL_REQUESTED -> AgentLifecycleState.CANCELLING
            else -> illegal(state, event)
        }

        AgentLifecycleState.CANCELLING -> when (event) {
            AgentLifecycleEvent.CANCELLATION_COMPLETE -> AgentLifecycleState.CANCELLED
            AgentLifecycleEvent.CANCELLATION_FAILED -> AgentLifecycleState.FAILED
            else -> illegal(state, event)
        }

        AgentLifecycleState.SUCCEEDED,
        AgentLifecycleState.CANCELLED,
        AgentLifecycleState.BLOCKED,
        AgentLifecycleState.FAILED -> when (event) {
            AgentLifecycleEvent.TERMINAL_ACKNOWLEDGED -> AgentLifecycleState.IDLE
            else -> illegal(state, event)
        }
    }

    private fun illegal(
        state: AgentLifecycleState,
        event: AgentLifecycleEvent,
    ): Nothing = throw IllegalAgentLifecycleTransition(state, event)
}

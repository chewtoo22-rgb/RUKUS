package com.ruckus.agent.core

data class AgentTaskState(
    val request: String,
    val currentStep: Int,
    val totalSteps: Int,
    val lastAction: AgentAction?,
    val lastScreenSummary: String?,
    val recoveryAttempts: Int,
    val status: Status
) {
    enum class Status { IDLE, RUNNING, EXECUTING, RECOVERING, WAITING_CONFIRMATION, FAILED, COMPLETE }

    fun validationError(): String? {
        if (totalSteps < 0) return "totalSteps must be non-negative"
        if (currentStep !in 0..totalSteps) return "currentStep must be within 0..totalSteps"
        if (recoveryAttempts < 0) return "recoveryAttempts must be non-negative"

        if (status == Status.IDLE) {
            if (request.isNotEmpty()) return "IDLE state must not retain a request"
            if (currentStep != 0 || totalSteps != 0) return "IDLE state must have zero progress"
            if (lastAction != null) return "IDLE state must not retain an action"
            if (recoveryAttempts != 0) return "IDLE state must not retain recovery attempts"
            return null
        }

        if (request.isBlank()) return "non-IDLE state requires a request"

        when (status) {
            Status.EXECUTING, Status.RECOVERING, Status.WAITING_CONFIRMATION -> {
                if (totalSteps == 0 || currentStep >= totalSteps) {
                    return "$status state requires an in-range pending step"
                }
                if (lastAction == null) return "$status state requires a current action"
            }
            Status.COMPLETE -> {
                if (totalSteps == 0) return "COMPLETE state requires at least one step"
                if (currentStep != totalSteps) return "COMPLETE state requires all steps complete"
                if (lastAction == null) return "COMPLETE state requires the final action"
            }
            Status.IDLE, Status.RUNNING, Status.FAILED -> Unit
        }
        return null
    }

    fun isValid(): Boolean = validationError() == null
}

object AgentTaskStateStore {
    private val idle = AgentTaskState("", 0, 0, null, null, 0, AgentTaskState.Status.IDLE)

    @Volatile private var state = idle

    fun get(): AgentTaskState = state

    fun set(value: AgentTaskState) {
        val error = value.validationError()
        require(error == null) { "Invalid agent task state: $error" }
        state = value
    }

    fun clear() { state = idle }
}

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
}

object AgentTaskStateStore {
    @Volatile private var state = AgentTaskState("",0,0,null,null,0,AgentTaskState.Status.IDLE)
    fun get(): AgentTaskState = state
    fun set(value: AgentTaskState) { state = value }
    fun clear() { state = AgentTaskState("",0,0,null,null,0,AgentTaskState.Status.IDLE) }
}

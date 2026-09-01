package com.ruckus.agent.core

/**
 * Preserves the executor's crash-recovery invariant: a state is not observable in memory
 * until the matching checkpoint has been durably accepted by storage.
 */
internal object DurableCheckpointPublisher {
    fun publish(
        state: AgentTaskState,
        persist: (AgentTaskState) -> Unit,
        publishInMemory: (AgentTaskState) -> Unit
    ) {
        persist(state)
        publishInMemory(state)
    }
}

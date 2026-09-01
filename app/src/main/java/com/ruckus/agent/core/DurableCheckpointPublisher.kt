package com.ruckus.agent.core

internal class CheckpointPersistenceException(cause: RuntimeException) :
    IllegalStateException("Durable task checkpoint unavailable", cause)

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
        try {
            persist(state)
        } catch (failure: RuntimeException) {
            throw CheckpointPersistenceException(failure)
        }
        publishInMemory(state)
    }
}

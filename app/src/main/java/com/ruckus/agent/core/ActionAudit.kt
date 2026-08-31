package com.ruckus.agent.core

import java.util.ArrayDeque

data class ActionRecord(val atMs: Long, val request: String, val action: String, val outcome: String)

object ActionAudit {
    private const val MAX = 100
    private val lock = Any()
    private val records = ArrayDeque<ActionRecord>(MAX)

    fun record(request: String, action: AgentAction?, outcome: String) {
        val record = ActionRecord(System.currentTimeMillis(), request, action?.toString() ?: "NONE", outcome)
        synchronized(lock) {
            records.addFirst(record)
            while (records.size > MAX) records.removeLast()
        }
        ExecutionHealthTelemetry.record(outcome)
    }

    fun recent(limit: Int = 20): List<ActionRecord> {
        if (limit <= 0) return emptyList()
        return synchronized(lock) {
            records.asSequence().take(limit.coerceAtMost(MAX)).toList()
        }
    }

    internal fun storedCountForTests(): Int = synchronized(lock) { records.size }

    internal fun resetForTests() {
        synchronized(lock) {
            records.clear()
        }
    }
}

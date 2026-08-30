package com.ruckus.agent.core

import java.util.concurrent.ConcurrentLinkedDeque

data class ActionRecord(val atMs: Long, val request: String, val action: String, val outcome: String)

object ActionAudit {
    private const val MAX = 100
    private val records = ConcurrentLinkedDeque<ActionRecord>()

    fun record(request: String, action: AgentAction?, outcome: String) {
        records.addFirst(ActionRecord(System.currentTimeMillis(), request, action?.toString() ?: "NONE", outcome))
        while (records.size > MAX) records.pollLast()
        ExecutionHealthTelemetry.record(outcome)
    }

    fun recent(limit: Int = 20): List<ActionRecord> = records.take(limit.coerceIn(1, MAX))
}

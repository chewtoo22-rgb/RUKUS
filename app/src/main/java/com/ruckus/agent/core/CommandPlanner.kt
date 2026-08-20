package com.ruckus.agent.core

/** Turns simple chained language into a deterministic action sequence. */
object CommandPlanner {
    data class Plan(val actions: List<AgentAction>, val rejectedParts: List<String>)

    fun plan(raw: String): Plan {
        val parts = raw
            .split(Regex("\\s+(?:and then|then|and)\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val actions = mutableListOf<AgentAction>()
        val rejected = mutableListOf<String>()
        for (part in parts) {
            val parsed = CommandParser.parse(part)
            if (parsed.action != null) actions += parsed.action else rejected += part
        }
        return Plan(actions, rejected)
    }
}

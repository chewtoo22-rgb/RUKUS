package com.ruckus.agent.core

/** Turns simple chained language into a deterministic, bounded action sequence. */
object CommandPlanner {
    data class Plan(val actions: List<AgentAction>, val rejectedParts: List<String>)

    fun plan(raw: String): Plan {
        val goal = GoalAdmissionPolicy.evaluate(raw)
        if (!goal.allowed) {
            return Plan(emptyList(), listOf("goal rejected: ${goal.reason}"))
        }

        val parts = goal.normalizedGoal
            .split(Regex("\\s+(?:and then|then|and)\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val actions = mutableListOf<AgentAction>()
        val rejected = mutableListOf<String>()
        for (part in parts) {
            val parsed = CommandParser.parse(part)
            if (parsed.action != null) actions += parsed.action else rejected += part
        }

        if (actions.isNotEmpty()) {
            val admission = PlanAdmissionPolicy.evaluate(actions)
            if (!admission.allowed) {
                rejected += "plan rejected: ${admission.reason}"
                // A structurally rejected plan is evidence only, never an executable partial.
                // Keep the rejection details for UX/diagnostics while exposing zero typed actions
                // to downstream callers that may not independently re-run admission.
                return Plan(emptyList(), rejected)
            }
        }

        return Plan(actions, rejected)
    }
}

package com.ruckus.agent.core

/** Turns simple chained language into a deterministic, bounded action sequence. */
object CommandPlanner {
    data class Plan(val actions: List<AgentAction>, val rejectedParts: List<String>)

    private val sequenceBoundary = Regex(
        """\s+(?:and then|then)\s+(?=(?:go home|home|go back|back|open package\s+|open\s+|scroll down|swipe up|scroll up|swipe down|inspect screen|what's on screen|whats on screen|read screen|tap\s+|click\s+|type\s+|brightness\s+|volume\s+))""",
        RegexOption.IGNORE_CASE
    )

    fun plan(raw: String): Plan {
        val goal = GoalAdmissionPolicy.evaluate(raw)
        if (!goal.allowed) {
            return Plan(emptyList(), listOf("goal rejected: ${goal.reason}"))
        }

        // Treat sequencing language as a boundary only when the following fragment can begin a
        // supported deterministic command. This preserves ordinary argument text such as
        // "type better then ever" while retaining chains such as "home then open settings".
        val parts = goal.normalizedGoal
            .split(sequenceBoundary)
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

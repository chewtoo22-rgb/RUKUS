package com.ruckus.agent.core

/** Turns simple chained language into a deterministic, bounded action sequence. */
object CommandPlanner {
    data class Plan(val actions: List<AgentAction>, val rejectedParts: List<String>)

    private val sequencingPhrase = Regex(
        """\s+(?:and then|then)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val supportedCommandStart = Regex(
        """^(?:go home|home|go back|back|open package\s+|open\s+|scroll down|swipe up|scroll up|swipe down|inspect screen|what's on screen|whats on screen|read screen|tap\s+|click\s+|type\s+|brightness\s+|volume\s+)""",
        RegexOption.IGNORE_CASE
    )

    private val fixedCommand = Regex(
        """^(?:go home|home|go back|back|scroll down|swipe up|scroll up|swipe down|inspect screen|what's on screen|whats on screen|read screen)$""",
        RegexOption.IGNORE_CASE
    )

    fun plan(raw: String): Plan {
        val goal = GoalAdmissionPolicy.evaluate(raw)
        if (!goal.allowed) {
            return Plan(emptyList(), listOf("goal rejected: ${goal.reason}"))
        }

        // A sequencing phrase is a boundary when the right side starts a supported command, or
        // when the left side is a complete fixed command. The latter preserves fail-closed
        // partial-plan evidence such as "home then make coffee", while free-form arguments like
        // "type better then ever" and "open Better Then Ezra" remain intact.
        val parts = splitSequence(goal.normalizedGoal)

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

    private fun splitSequence(goal: String): List<String> {
        val parts = mutableListOf<String>()
        var partStart = 0

        for (match in sequencingPhrase.findAll(goal)) {
            val left = goal.substring(partStart, match.range.first).trim()
            val rightStart = match.range.last + 1
            val right = goal.substring(rightStart).trimStart()

            val isBoundary = supportedCommandStart.containsMatchIn(right) || fixedCommand.matches(left)
            if (isBoundary) {
                if (left.isNotEmpty()) parts += left
                partStart = rightStart
            }
        }

        val tail = goal.substring(partStart).trim()
        if (tail.isNotEmpty()) parts += tail
        return parts
    }
}

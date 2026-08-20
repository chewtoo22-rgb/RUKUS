package com.ruckus.agent.core

/** Deterministic first-pass parser. Unknown language never executes implicitly. */
object CommandParser {
    data class Parsed(val action: AgentAction?, val confidence: Float, val explanation: String)

    fun parse(raw: String): Parsed {
        val q = raw.trim().lowercase()
        if (q.isBlank()) return Parsed(null, 0f, "Empty command")
        return when {
            q == "go home" || q == "home" -> Parsed(AgentAction.Global("HOME"), .99f, "Go to home screen")
            q == "go back" || q == "back" -> Parsed(AgentAction.Global("BACK"), .99f, "Go back")
            q == "open notifications" || q == "notifications" -> Parsed(AgentAction.Global("NOTIFICATIONS"), .98f, "Open notifications")
            q.startsWith("open ") -> Parsed(AgentAction.LaunchApp(q.removePrefix("open ").trim()), .90f, "Launch requested app")
            q.startsWith("tap ") -> Parsed(AgentAction.TapText(q.removePrefix("tap ").trim()), .88f, "Tap matching visible text")
            q.startsWith("type ") -> Parsed(AgentAction.TypeText(raw.trim().substringAfter(" ")), .86f, "Type requested text")
            q == "scroll down" -> Parsed(AgentAction.Scroll("DOWN"), .96f, "Scroll down")
            q == "scroll up" -> Parsed(AgentAction.Scroll("UP"), .96f, "Scroll up")
            else -> Parsed(null, .20f, "No safe deterministic action matched")
        }
    }
}

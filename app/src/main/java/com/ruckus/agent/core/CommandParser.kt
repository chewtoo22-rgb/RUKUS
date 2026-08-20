package com.ruckus.agent.core

/** Deterministic first-pass parser. Unknown language never executes implicitly. */
object CommandParser {
    data class Parsed(val action: AgentAction?, val confidence: Float, val explanation: String)
    fun parse(raw: String): Parsed {
        val q = raw.trim().lowercase()
        if (q.isBlank()) return Parsed(null, 0f, "Empty command")
        return when {
            q == "go home" || q == "home" -> Parsed(AgentAction.Home, .99f, "Go home")
            q == "go back" || q == "back" -> Parsed(AgentAction.Back, .99f, "Go back")
            q.startsWith("open package ") -> Parsed(AgentAction.OpenApp(q.removePrefix("open package ").trim()), .98f, "Launch exact package")
            q.startsWith("type ") -> Parsed(AgentAction.TypeText(raw.trim().substringAfter(" ")), .86f, "Type requested text")
            q.startsWith("brightness ") -> q.removePrefix("brightness ").removeSuffix("%").toIntOrNull()?.coerceIn(0,100)?.let { Parsed(AgentAction.SetBrightness(it), .96f, "Set brightness") } ?: Parsed(null,.1f,"Invalid brightness")
            q.startsWith("volume ") -> q.removePrefix("volume ").removeSuffix("%").toIntOrNull()?.coerceIn(0,100)?.let { Parsed(AgentAction.SetMediaVolume(it), .96f, "Set media volume") } ?: Parsed(null,.1f,"Invalid volume")
            else -> Parsed(null, .20f, "No safe deterministic action matched")
        }
    }
}

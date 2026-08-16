package com.ruckus.agent.core

data class AgentCommand(
    val agent: AgentIdentity,
    val intent: String,
    val arguments: Map<String, String> = emptyMap(),
    val userText: String
)

data class AgentReply(
    val agent: AgentIdentity,
    val message: String,
    val actions: List<AgentAction> = emptyList()
)

object CommandProtocol {
    fun parseRukus(text: String): AgentCommand {
        val normalized = text.trim()
        val lower = normalized.lowercase()
        val intent = when {
            lower.startsWith("open ") -> "open_app"
            lower.startsWith("click ") || lower.startsWith("tap ") -> "click_text"
            lower.startsWith("type ") -> "type_text"
            lower.contains("read screen") || lower == "what's on screen" || lower == "whats on screen" -> "read_screen"
            lower == "back" || lower.contains("go back") -> "back"
            lower == "home" || lower.contains("go home") -> "home"
            lower.contains("brightness") -> "set_brightness"
            lower.contains("volume") -> "set_volume"
            else -> "unknown"
        }
        return AgentCommand(AgentIdentity.RUKUS, intent, userText = normalized)
    }
}

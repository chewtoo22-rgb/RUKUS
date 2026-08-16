package com.ruckus.agent.core

object RukusCommandRouter {
    fun route(command: AgentCommand): AgentReply {
        require(command.agent == AgentIdentity.RUKUS)
        val raw = command.userText.trim()
        val lower = raw.lowercase()
        val actions = when (command.intent) {
            "read_screen" -> listOf(AgentAction.ReadScreen)
            "click_text" -> listOf(AgentAction.ClickText(raw.substringAfter(' ').trim()))
            "type_text" -> listOf(AgentAction.TypeText(raw.substringAfter(' ').trim()))
            "back" -> listOf(AgentAction.Back)
            "home" -> listOf(AgentAction.Home)
            "set_brightness" -> percentFrom(lower)?.let { listOf(AgentAction.SetBrightness(it)) }.orEmpty()
            "set_volume" -> percentFrom(lower)?.let { listOf(AgentAction.SetMediaVolume(it)) }.orEmpty()
            else -> emptyList()
        }
        val message = if (actions.isEmpty()) {
            "I understood the request, but I don't have a safe executable mapping for it yet."
        } else {
            "Queued ${actions.size} typed action${if (actions.size == 1) "" else "s"}."
        }
        return AgentReply(AgentIdentity.RUKUS, message, actions)
    }

    private fun percentFrom(text: String): Int? = Regex("(\\d{1,3})\\s*%?")
        .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
}

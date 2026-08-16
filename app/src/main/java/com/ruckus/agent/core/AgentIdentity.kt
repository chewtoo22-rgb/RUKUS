package com.ruckus.agent.core

enum class AgentIdentity {
    RUKUS,
    MUTINY
}

data class AgentSession(
    val identity: AgentIdentity,
    val title: String,
    val role: String,
    val tagline: String
)

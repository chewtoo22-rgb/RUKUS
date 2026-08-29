package com.ruckus.agent.core

enum class AgentIdentity {
    RUKUS,
    NITRO,
    /** Legacy identity retained only while persisted/stacked MUTINY work migrates. */
    @Deprecated("Use NITRO")
    MUTINY;

    fun canonical(): AgentIdentity = if (this == MUTINY) NITRO else this
}

data class AgentSession(
    val identity: AgentIdentity,
    val title: String,
    val role: String,
    val tagline: String
)

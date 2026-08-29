package com.ruckus.agent.personality

/**
 * Compatibility shim while Phase 2-4 branches migrate from the former MUTINY name.
 * New code should reference NitroPersona directly.
 */
@Deprecated("Renamed to NitroPersona")
object MutinyPersona {
    const val NAME = NitroPersona.NAME
    const val TAGLINE = NitroPersona.TAGLINE
    const val ROLE = NitroPersona.ROLE
    const val SYSTEM_STYLE = NitroPersona.SYSTEM_STYLE
}

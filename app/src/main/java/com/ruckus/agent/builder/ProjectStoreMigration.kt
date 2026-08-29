package com.ruckus.agent.builder

internal object ProjectStoreMigration {
    const val CURRENT_NAMESPACE = "nitro_projects"
    const val LEGACY_NAMESPACE = "mutiny_projects"

    fun planCopy(
        legacyEntries: Map<String, *>,
        currentKeys: Set<String>
    ): Map<String, String> = legacyEntries
        .asSequence()
        .filter { (key, value) -> key.isNotBlank() && value is String && key !in currentKeys }
        .associate { (key, value) -> key to (value as String) }
}

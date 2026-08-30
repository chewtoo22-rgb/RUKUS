package com.ruckus.agent.core

/**
 * Deterministic app-name resolution policy used before dispatch.
 *
 * Exact labels win only when they identify one launchable package. Otherwise a
 * partial label is accepted only when it uniquely identifies one package. Ambiguous
 * names fail closed instead of allowing PackageManager iteration order to choose.
 */
object AppLaunchMatchPolicy {
    data class Candidate(val packageName: String, val label: String)

    fun resolve(requestedName: String, candidates: List<Candidate>): Candidate? {
        val requested = requestedName.trim()
        if (requested.isEmpty()) return null

        val normalized = candidates
            .asSequence()
            .filter { it.packageName.isNotBlank() && it.label.isNotBlank() }
            .distinctBy { it.packageName }
            .toList()

        val exact = normalized.filter { it.label.equals(requested, ignoreCase = true) }
        if (exact.size == 1) return exact.single()
        if (exact.size > 1) return null

        val partial = normalized.filter { it.label.contains(requested, ignoreCase = true) }
        return partial.singleOrNull()
    }
}

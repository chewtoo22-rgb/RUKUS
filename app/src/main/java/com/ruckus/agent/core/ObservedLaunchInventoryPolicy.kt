package com.ruckus.agent.core

/**
 * Keeps launcher observations bounded while guaranteeing that the currently
 * foreground launchable package is represented when it exists in the inventory.
 *
 * Natural-language app completion is proven from the final observation. A simple
 * alphabetical cap can otherwise omit the app that was just opened on devices with
 * many launchable apps, causing a false "not proven" result despite correct dispatch.
 */
object ObservedLaunchInventoryPolicy {
    const val MAX_OBSERVED_APPS = 32

    fun select(
        candidates: List<AppLaunchMatchPolicy.Candidate>,
        foregroundPackage: String?
    ): List<AppLaunchMatchPolicy.Candidate> {
        val normalized = candidates
            .asSequence()
            .filter { it.packageName.isNotBlank() && it.label.isNotBlank() }
            .distinct()
            .sortedWith(
                compareBy<AppLaunchMatchPolicy.Candidate>(
                    { it.packageName.lowercase() },
                    { it.label.lowercase() }
                )
            )
            .toList()

        val foreground = foregroundPackage
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { pkg -> normalized.filter { it.packageName.equals(pkg, ignoreCase = true) } }
            .orEmpty()

        val remainder = normalized.filterNot { candidate ->
            foreground.any { it == candidate }
        }

        return (foreground + remainder).take(MAX_OBSERVED_APPS)
    }
}

package com.ruckus.agent.core

import android.content.Context
import android.content.Intent

/**
 * Single deterministic resolver for natural-language app launch targets.
 *
 * Whole-plan readiness and final dispatch must consult the same inventory/filtering
 * rules so Android package enumeration order cannot make the two gates disagree.
 */
object InstalledAppLaunchResolver {
    fun resolve(context: Context, appName: String): AppLaunchMatchPolicy.Candidate? {
        val packageManager = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = packageManager.queryIntentActivities(query, 0).mapNotNull { info ->
            val packageName = info.activityInfo?.packageName?.trim().orEmpty()
            val label = runCatching { info.loadLabel(packageManager).toString().trim() }.getOrDefault("")
            if (packageName.isBlank() || label.isBlank()) null
            else AppLaunchMatchPolicy.Candidate(packageName, label)
        }.filter { candidate ->
            packageManager.getLaunchIntentForPackage(candidate.packageName) != null
        }
        return AppLaunchMatchPolicy.resolve(appName, candidates)
    }
}

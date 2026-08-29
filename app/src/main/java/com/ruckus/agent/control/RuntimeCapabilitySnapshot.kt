package com.ruckus.agent.control

import android.content.Context
import android.provider.Settings

/**
 * Single fail-closed view of the Android capabilities the executor may rely on.
 *
 * Keeping these checks together prevents UI, preflight, and future onboarding flows
 * from each inventing slightly different definitions of "ready".
 */
data class RuntimeCapabilitySnapshot(
    val accessibilityReady: Boolean,
    val writeSettingsReady: Boolean,
    val shizukuBinderAvailable: Boolean,
    val shizukuPermissionGranted: Boolean,
    val approvedShellAdapterEnabled: Boolean
) {
    val approvedShellReady: Boolean
        get() = approvedShellAdapterEnabled && shizukuBinderAvailable && shizukuPermissionGranted
}

object RuntimeCapabilityReader {
    fun read(
        context: Context,
        approvedShellAdapterEnabled: Boolean = false
    ): RuntimeCapabilitySnapshot {
        val appContext = context.applicationContext
        val shizuku = runCatching { ShizukuStateReader.read() }
            .getOrElse { ShizukuState(binderAvailable = false, permissionGranted = false) }

        return RuntimeCapabilitySnapshot(
            accessibilityReady = RuckusAccessibilityService.instance != null,
            writeSettingsReady = runCatching { Settings.System.canWrite(appContext) }.getOrDefault(false),
            shizukuBinderAvailable = shizuku.binderAvailable,
            shizukuPermissionGranted = shizuku.permissionGranted,
            approvedShellAdapterEnabled = approvedShellAdapterEnabled
        )
    }
}

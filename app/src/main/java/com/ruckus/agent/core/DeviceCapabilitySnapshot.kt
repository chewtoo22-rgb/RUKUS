package com.ruckus.agent.core

import android.content.Context
import android.provider.Settings
import com.ruckus.agent.control.RuckusAccessibilityService
import com.ruckus.agent.control.ShizukuState
import com.ruckus.agent.control.ShizukuStateReader

enum class DeviceCapability {
    ACCESSIBILITY_CONTROL,
    WRITE_SETTINGS,
    SHIZUKU_BINDER,
    SHIZUKU_PERMISSION,
    APPROVED_SHELL
}

data class DeviceCapabilityStatus(
    val capability: DeviceCapability,
    val ready: Boolean,
    val remediation: String
)

data class DeviceCapabilitySnapshot(
    val statuses: List<DeviceCapabilityStatus>
) {
    fun isReady(capability: DeviceCapability): Boolean =
        statuses.firstOrNull { it.capability == capability }?.ready == true

    fun missing(): List<DeviceCapabilityStatus> = statuses.filterNot { it.ready }

    val basicControlReady: Boolean
        get() = isReady(DeviceCapability.ACCESSIBILITY_CONTROL)

    val brightnessControlReady: Boolean
        get() = basicControlReady && isReady(DeviceCapability.WRITE_SETTINGS)

    val privilegedControlReady: Boolean
        get() = basicControlReady && isReady(DeviceCapability.APPROVED_SHELL)
}

/**
 * Single source of truth for user-facing device readiness.
 *
 * Command preflight remains action-specific, while this snapshot gives onboarding,
 * settings, diagnostics and hardware-test tooling a stable description of the current
 * Android capability state before a command is attempted.
 */
object DeviceCapabilityReader {
    fun read(context: Context): DeviceCapabilitySnapshot {
        val appContext = context.applicationContext
        val shizuku = safeShizukuStateRead { ShizukuStateReader.read() }
        return fromStates(
            accessibilityReady = RuckusAccessibilityService.instance != null,
            writeSettingsReady = safeWriteSettingsRead { Settings.System.canWrite(appContext) },
            shizukuBinderAvailable = shizuku?.binderAvailable == true,
            shizukuPermissionGranted = shizuku?.permissionGranted == true,
            approvedShellReady = false // Bounded Shizuku shell adapter is not implemented yet.
        )
    }

    internal fun safeShizukuStateRead(read: () -> ShizukuState): ShizukuState? =
        try {
            read()
        } catch (_: RuntimeException) {
            null
        }

    internal fun safeWriteSettingsRead(read: () -> Boolean): Boolean =
        try {
            read()
        } catch (_: RuntimeException) {
            false
        }

    internal fun fromStates(
        accessibilityReady: Boolean,
        writeSettingsReady: Boolean,
        shizukuBinderAvailable: Boolean,
        shizukuPermissionGranted: Boolean,
        approvedShellReady: Boolean
    ): DeviceCapabilitySnapshot = DeviceCapabilitySnapshot(
        listOf(
            DeviceCapabilityStatus(
                DeviceCapability.ACCESSIBILITY_CONTROL,
                accessibilityReady,
                "Enable RUKUS in Android Accessibility settings"
            ),
            DeviceCapabilityStatus(
                DeviceCapability.WRITE_SETTINGS,
                writeSettingsReady,
                "Allow RUKUS to modify system settings for brightness control"
            ),
            DeviceCapabilityStatus(
                DeviceCapability.SHIZUKU_BINDER,
                shizukuBinderAvailable,
                "Start Shizuku before using privileged device actions"
            ),
            DeviceCapabilityStatus(
                DeviceCapability.SHIZUKU_PERMISSION,
                shizukuBinderAvailable && shizukuPermissionGranted,
                if (shizukuBinderAvailable) {
                    "Grant RUKUS permission in Shizuku"
                } else {
                    "Start Shizuku, then grant RUKUS permission"
                }
            ),
            DeviceCapabilityStatus(
                DeviceCapability.APPROVED_SHELL,
                approvedShellReady && shizukuBinderAvailable && shizukuPermissionGranted,
                "Privileged command adapter is not available in this build"
            )
        )
    )
}

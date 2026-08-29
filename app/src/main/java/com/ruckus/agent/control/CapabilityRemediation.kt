package com.ruckus.agent.control

/**
 * Typed remediation contract for Android capabilities required by RUKUS.
 *
 * The UI/onboarding layer should consume these steps instead of inventing its own
 * readiness rules. Steps are ordered so the user can recover the capabilities that
 * are currently actionable without implying that the intentionally-disabled shell
 * adapter is available.
 */
enum class CapabilityRemediationKind {
    ENABLE_ACCESSIBILITY,
    GRANT_WRITE_SETTINGS,
    START_SHIZUKU,
    GRANT_SHIZUKU_PERMISSION,
    ENABLE_APPROVED_SHELL_ADAPTER
}

data class CapabilityRemediationStep(
    val kind: CapabilityRemediationKind,
    val userActionable: Boolean
)

object CapabilityRemediationPlanner {
    fun plan(snapshot: RuntimeCapabilitySnapshot): List<CapabilityRemediationStep> = buildList {
        if (!snapshot.accessibilityReady) {
            add(CapabilityRemediationStep(CapabilityRemediationKind.ENABLE_ACCESSIBILITY, true))
        }
        if (!snapshot.writeSettingsReady) {
            add(CapabilityRemediationStep(CapabilityRemediationKind.GRANT_WRITE_SETTINGS, true))
        }

        if (!snapshot.shizukuBinderAvailable) {
            add(CapabilityRemediationStep(CapabilityRemediationKind.START_SHIZUKU, true))
        } else if (!snapshot.shizukuPermissionGranted) {
            add(CapabilityRemediationStep(CapabilityRemediationKind.GRANT_SHIZUKU_PERMISSION, true))
        }

        if (!snapshot.approvedShellAdapterEnabled) {
            add(CapabilityRemediationStep(CapabilityRemediationKind.ENABLE_APPROVED_SHELL_ADAPTER, false))
        }
    }
}

enum class ShizukuPermissionDecision {
    INVALID_REQUEST_CODE,
    SERVICE_UNAVAILABLE,
    ALREADY_GRANTED,
    REQUEST_PERMISSION
}

object ShizukuPermissionPlanner {
    fun decide(state: ShizukuState, requestCode: Int): ShizukuPermissionDecision = when {
        requestCode < 0 -> ShizukuPermissionDecision.INVALID_REQUEST_CODE
        !state.binderAvailable -> ShizukuPermissionDecision.SERVICE_UNAVAILABLE
        state.permissionGranted -> ShizukuPermissionDecision.ALREADY_GRANTED
        else -> ShizukuPermissionDecision.REQUEST_PERMISSION
    }
}

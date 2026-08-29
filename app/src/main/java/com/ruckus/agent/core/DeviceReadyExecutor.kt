package com.ruckus.agent.core

import android.content.Context
import android.provider.Settings
import com.ruckus.agent.control.RuckusAccessibilityService

/**
 * App-facing executor facade that fails closed before any side effect when the
 * observation/control capabilities required by the remaining plan are unavailable.
 */
class DeviceReadyExecutor(context: Context) {
    private val appContext = context.applicationContext
    private val delegate = RuckusExecutor(appContext)

    fun lastSession(): PersistedTaskSession? = delegate.lastSession()

    /**
     * Reusable readiness snapshot for onboarding, settings, diagnostics and hands-on
     * device validation. This does not replace action-specific whole-plan preflight.
     */
    fun capabilities(): DeviceCapabilitySnapshot = DeviceCapabilityReader.read(appContext)

    fun run(request: String, approved: Boolean = false): ExecutionReport {
        val plan = CommandPlanner.plan(request)
        if (plan.actions.isEmpty() || plan.rejectedParts.isNotEmpty()) {
            return delegate.run(request, approved)
        }
        preflight(plan.actions, 0)?.let { return it }
        return delegate.run(request, approved)
    }

    fun resumeLast(approved: Boolean = false): ExecutionReport {
        val session = delegate.lastSession() ?: return delegate.resumeLast(approved)
        val plan = CommandPlanner.plan(session.request)
        val resume = ResumePolicy.decide(session, plan)
        if (!resume.allowed) return delegate.resumeLast(approved)
        preflight(plan.actions, resume.startStep)?.let { return it }
        return delegate.resumeLast(approved)
    }

    private fun preflight(actions: List<AgentAction>, startStep: Int): ExecutionReport? {
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = startStep,
            accessibilityReady = RuckusAccessibilityService.instance != null,
            writeSettingsReady = Settings.System.canWrite(appContext),
            approvedShellReady = false, // DeviceController's bounded Shizuku adapter is not implemented yet.
            launchTargetReady = ::isLaunchTargetReady
        )
        if (decision.allowed) return null
        ActionAudit.record("device-readiness", decision.action, "DEVICE_PREFLIGHT_BLOCKED: ${decision.reason}")
        return ExecutionReport(
            ok = false,
            message = decision.reason,
            action = decision.action,
            completedSteps = startStep,
            totalSteps = actions.size
        )
    }

    private fun isLaunchTargetReady(action: AgentAction): Boolean = when (action) {
        is AgentAction.OpenApp -> appContext.packageManager
            .getLaunchIntentForPackage(action.packageName) != null
        is AgentAction.OpenAppByName -> InstalledAppLaunchResolver.resolve(appContext, action.appName) != null
        else -> true
    }
}

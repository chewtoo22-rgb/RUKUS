package com.ruckus.agent.core

import android.content.Context

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

    fun run(request: String, approved: Boolean = false): ExecutionReport = withExecutionLease {
        val plan = CommandPlanner.plan(request)
        if (plan.actions.isEmpty() || plan.rejectedParts.isNotEmpty()) {
            return@withExecutionLease delegate.run(request, approved)
        }
        preflight(plan.actions, 0)?.let { return@withExecutionLease it }
        delegate.run(request, approved)
    }

    fun resumeLast(approved: Boolean = false): ExecutionReport = withExecutionLease {
        val session = delegate.lastSession() ?: return@withExecutionLease delegate.resumeLast(approved)
        val plan = CommandPlanner.plan(session.request)
        val resume = ResumePolicy.decide(session, plan)
        if (!resume.allowed) return@withExecutionLease delegate.resumeLast(approved)
        preflight(plan.actions, resume.startStep)?.let { return@withExecutionLease it }
        delegate.resumeLast(approved)
    }

    private fun withExecutionLease(block: () -> ExecutionReport): ExecutionReport {
        if (!executionGate.tryAcquire()) {
            val reason = "Another RUKUS command is already executing. Wait for it to finish before starting or resuming another task."
            ActionAudit.record("execution-admission", null, "EXECUTION_BUSY_BLOCKED: $reason")
            return ExecutionReport(ok = false, message = reason)
        }
        return try {
            block()
        } finally {
            executionGate.release()
        }
    }

    private fun preflight(actions: List<AgentAction>, startStep: Int): ExecutionReport? {
        val capabilities = DeviceCapabilityReader.read(appContext)
        val decision = DeviceReadinessPreflight.evaluate(
            actions = actions,
            startStep = startStep,
            accessibilityReady = capabilities.isReady(DeviceCapability.ACCESSIBILITY_CONTROL),
            writeSettingsReady = capabilities.isReady(DeviceCapability.WRITE_SETTINGS),
            approvedShellReady = capabilities.isReady(DeviceCapability.APPROVED_SHELL),
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

    private companion object {
        val executionGate = ExecutionAdmissionGate()
    }
}

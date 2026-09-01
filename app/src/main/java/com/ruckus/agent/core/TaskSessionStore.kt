package com.ruckus.agent.core

import android.content.Context
import org.json.JSONObject

const val PERSISTED_SESSION_SCHEMA_VERSION = 3

class TaskSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("ruckus_task_session", Context.MODE_PRIVATE)

    fun save(state: AgentTaskState, planFingerprint: String? = null) {
        val base = PersistedTaskSession(
            request = state.request,
            currentStep = state.currentStep,
            totalSteps = state.totalSteps,
            lastAction = state.lastAction?.toString(),
            lastScreenSummary = state.lastScreenSummary,
            recoveryAttempts = state.recoveryAttempts,
            status = state.status,
            savedAtMs = System.currentTimeMillis(),
            planFingerprint = planFingerprint,
            schemaVersion = PERSISTED_SESSION_SCHEMA_VERSION,
            completionEvidenceDigest = null,
            checkpointDigest = null
        )
        val withCompletionEvidence = if (state.status == AgentTaskState.Status.COMPLETE) {
            base.copy(completionEvidenceDigest = TaskCompletionEvidence.compute(base))
        } else {
            base
        }
        val session = withCompletionEvidence.copy(
            checkpointDigest = PersistedSessionDigest.compute(withCompletionEvidence)
        )

        val json = JSONObject().apply {
            put("schemaVersion", session.schemaVersion)
            put("request", session.request)
            put("currentStep", session.currentStep)
            put("totalSteps", session.totalSteps)
            put("lastAction", session.lastAction)
            put("lastScreenSummary", session.lastScreenSummary)
            put("recoveryAttempts", session.recoveryAttempts)
            put("status", session.status.name)
            put("planFingerprint", session.planFingerprint)
            put("savedAt", session.savedAtMs)
            put("completionEvidenceDigest", session.completionEvidenceDigest)
            put("checkpointDigest", session.checkpointDigest)
        }

        // Checkpoints are part of the executor's safety boundary: EXECUTING is written
        // immediately before controller dispatch and verified progress is written before
        // the next step starts. commit() keeps that ordering durable across sudden process
        // death instead of allowing an asynchronous apply() write to lag behind execution.
        check(prefs.edit().putString(KEY, json.toString()).commit()) {
            "Unable to durably persist task checkpoint"
        }
    }

    fun load(): PersistedTaskSession? {
        val raw = SafePreferenceRead.stringOrNull { prefs.getString(KEY, null) } ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val session = PersistedTaskSession(
                request = json.optString("request"),
                currentStep = json.optInt("currentStep"),
                totalSteps = json.optInt("totalSteps"),
                lastAction = json.optString("lastAction").takeIf { it.isNotBlank() && it != "null" },
                lastScreenSummary = json.optString("lastScreenSummary").takeIf { it.isNotBlank() && it != "null" },
                recoveryAttempts = json.optInt("recoveryAttempts"),
                status = AgentTaskState.Status.valueOf(json.optString("status", AgentTaskState.Status.IDLE.name)),
                savedAtMs = json.optLong("savedAt"),
                planFingerprint = json.optString("planFingerprint").takeIf { it.isNotBlank() && it != "null" },
                schemaVersion = json.optInt("schemaVersion", 0),
                completionEvidenceDigest = json.optString("completionEvidenceDigest").takeIf { it.isNotBlank() && it != "null" },
                checkpointDigest = json.optString("checkpointDigest").takeIf { it.isNotBlank() && it != "null" }
            )

            val integrity = PersistedSessionIntegrityPolicy.evaluate(session)
            if (!integrity.allowed) {
                // Durable storage is not trusted execution authority. Refuse malformed,
                // impossible, partial, legacy, or corrupted checkpoints before resume.
                return@runCatching null
            }

            val resumeLease = SessionResumeLeasePolicy.evaluate(
                savedAtMs = session.savedAtMs,
                status = session.status
            )
            if (!resumeLease.allowed) {
                // A crash checkpoint is only evidence for a short-lived resume window. Beyond that
                // the phone/environment may have changed, so force a fresh task and observation.
                return@runCatching null
            }

            if (
                session.status == AgentTaskState.Status.WAITING_CONFIRMATION &&
                !ConfirmationLeasePolicy.evaluate(session.savedAtMs).allowed
            ) {
                // Expired approval checkpoints are demoted to RUNNING so resume
                // re-enters whole-plan preflight and asks for fresh confirmation.
                session.copy(status = AgentTaskState.Status.RUNNING)
            } else {
                session
            }
        }.getOrNull()
    }

    fun clear() {
        // Clearing is also synchronous so a subsequent launch cannot resurrect a task
        // whose checkpoint deletion was still buffered when the process disappeared.
        check(prefs.edit().remove(KEY).commit()) {
            "Unable to durably clear task checkpoint"
        }
    }

    companion object { private const val KEY = "active_task" }
}

data class PersistedTaskSession(
    val request: String,
    val currentStep: Int,
    val totalSteps: Int,
    val lastAction: String?,
    val lastScreenSummary: String?,
    val recoveryAttempts: Int,
    val status: AgentTaskState.Status,
    val savedAtMs: Long,
    val planFingerprint: String? = null,
    val schemaVersion: Int = PERSISTED_SESSION_SCHEMA_VERSION,
    val completionEvidenceDigest: String? = null,
    val checkpointDigest: String? = null
)

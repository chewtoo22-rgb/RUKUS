package com.ruckus.agent.core

import android.content.Context
import org.json.JSONObject

const val PERSISTED_SESSION_SCHEMA_VERSION = 1

class TaskSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("ruckus_task_session", Context.MODE_PRIVATE)

    fun save(state: AgentTaskState, planFingerprint: String? = null) {
        val json = JSONObject().apply {
            put("schemaVersion", PERSISTED_SESSION_SCHEMA_VERSION)
            put("request", state.request)
            put("currentStep", state.currentStep)
            put("totalSteps", state.totalSteps)
            put("lastAction", state.lastAction?.toString())
            put("lastScreenSummary", state.lastScreenSummary)
            put("recoveryAttempts", state.recoveryAttempts)
            put("status", state.status.name)
            put("planFingerprint", planFingerprint)
            put("savedAt", System.currentTimeMillis())
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun load(): PersistedTaskSession? {
        val raw = prefs.getString(KEY, null) ?: return null
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
                schemaVersion = json.optInt("schemaVersion", 0)
            )

            val integrity = PersistedSessionIntegrityPolicy.evaluate(session)
            if (!integrity.allowed) {
                // Durable storage is not trusted execution authority. Refuse malformed,
                // impossible, partial, legacy, or externally-modified checkpoints before resume.
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

    fun clear() = prefs.edit().remove(KEY).apply()

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
    val schemaVersion: Int = PERSISTED_SESSION_SCHEMA_VERSION
)

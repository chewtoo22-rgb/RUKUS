package com.ruckus.agent.core

import android.content.Context
import org.json.JSONObject

class TaskSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("ruckus_task_session", Context.MODE_PRIVATE)

    fun save(state: AgentTaskState) {
        val json = JSONObject().apply {
            put("request", state.request)
            put("currentStep", state.currentStep)
            put("totalSteps", state.totalSteps)
            put("lastAction", state.lastAction?.toString())
            put("lastScreenSummary", state.lastScreenSummary)
            put("recoveryAttempts", state.recoveryAttempts)
            put("status", state.status.name)
            put("savedAt", System.currentTimeMillis())
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun load(): PersistedTaskSession? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            PersistedTaskSession(
                request = json.optString("request"),
                currentStep = json.optInt("currentStep"),
                totalSteps = json.optInt("totalSteps"),
                lastAction = json.optString("lastAction").takeIf { it.isNotBlank() && it != "null" },
                lastScreenSummary = json.optString("lastScreenSummary").takeIf { it.isNotBlank() && it != "null" },
                recoveryAttempts = json.optInt("recoveryAttempts"),
                status = AgentTaskState.Status.valueOf(json.optString("status", AgentTaskState.Status.IDLE.name)),
                savedAtMs = json.optLong("savedAt")
            )
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
    val savedAtMs: Long
)

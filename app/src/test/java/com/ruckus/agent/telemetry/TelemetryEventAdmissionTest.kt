package com.ruckus.agent.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEventAdmissionTest {
    @Test
    fun acceptsBoundedOperationalMetadata() {
        val result = TelemetryEventAdmission.admit(
            TelemetryEventAdmission.Candidate(
                name = "agent_task_completed",
                attributes = mapOf(
                    "result" to "success",
                    "executor" to "shizuku",
                    "duration_bucket" to "1_5s"
                )
            )
        )

        assertTrue(result is TelemetryEventAdmission.Result.Accepted)
        val event = (result as TelemetryEventAdmission.Result.Accepted).event
        assertEquals("agent_task_completed", event.name)
        assertEquals(listOf("duration_bucket", "executor", "result"), event.attributes.keys.toList())
    }

    @Test
    fun rejectsFreeFormCommandMaterial() {
        assertRejected(mapOf("command" to "open banking app"))
        assertRejected(mapOf("prompt_text" to "do something"))
        assertRejected(mapOf("screen_text" to "private UI text"))
        assertRejected(mapOf("clipboard_value" to "copied secret"))
    }

    @Test
    fun rejectsIdentityAndLocationLikeFields() {
        assertRejected(mapOf("email_hash" to "deadbeef"))
        assertRejected(mapOf("phone_region" to "us"))
        assertRejected(mapOf("package_name" to "com.example.app"))
        assertRejected(mapOf("file_path" to "/sdcard/Download/a.txt"))
        assertRejected(mapOf("target_url" to "https://example.invalid"))
    }

    @Test
    fun rejectsSecretsAndTokensEvenWhenApparentlyRedacted() {
        assertRejected(mapOf("auth_token_present" to "false"))
        assertRejected(mapOf("secret_kind" to "none"))
        assertRejected(mapOf("password_length" to "12"))
    }

    @Test
    fun rejectsMalformedNamesAndValues() {
        assertTrue(TelemetryEventAdmission.admit(TelemetryEventAdmission.Candidate("TaskDone")) is TelemetryEventAdmission.Result.Rejected)
        assertTrue(TelemetryEventAdmission.admit(TelemetryEventAdmission.Candidate("task-done")) is TelemetryEventAdmission.Result.Rejected)
        assertRejected(mapOf("result" to " success"))
        assertRejected(mapOf("result" to "success\nnext"))
    }

    @Test
    fun rejectsAttributeFloodAndOversizedValues() {
        val tooMany = (1..13).associate { "field_$it" to "ok" }
        assertTrue(
            TelemetryEventAdmission.admit(
                TelemetryEventAdmission.Candidate("agent_state", tooMany)
            ) is TelemetryEventAdmission.Result.Rejected
        )

        assertRejected(mapOf("result" to "x".repeat(161)))
    }

    @Test
    fun outputOrderingIsDeterministic() {
        val result = TelemetryEventAdmission.admit(
            TelemetryEventAdmission.Candidate(
                "agent_state",
                linkedMapOf("zeta" to "z", "alpha" to "a", "middle" to "m")
            )
        ) as TelemetryEventAdmission.Result.Accepted

        assertEquals(listOf("alpha", "middle", "zeta"), result.event.attributes.keys.toList())
    }

    private fun assertRejected(attributes: Map<String, String>) {
        assertTrue(
            TelemetryEventAdmission.admit(
                TelemetryEventAdmission.Candidate("agent_state", attributes)
            ) is TelemetryEventAdmission.Result.Rejected
        )
    }
}

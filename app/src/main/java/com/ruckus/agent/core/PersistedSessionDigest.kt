package com.ruckus.agent.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Detects accidental or partial corruption of persisted executor checkpoints.
 *
 * This is an integrity checksum, not an authentication primitive: a privileged actor that can
 * rewrite app-private storage could also recompute it. Its purpose is to make crash/partial-write
 * corruption fail closed before resume logic sees a syntactically valid but semantically altered
 * checkpoint.
 */
object PersistedSessionDigest {
    fun compute(session: PersistedTaskSession): String {
        val canonical = buildString {
            field("schemaVersion", session.schemaVersion.toString())
            field("request", session.request)
            field("currentStep", session.currentStep.toString())
            field("totalSteps", session.totalSteps.toString())
            nullableField("lastAction", session.lastAction)
            nullableField("lastScreenSummary", session.lastScreenSummary)
            field("recoveryAttempts", session.recoveryAttempts.toString())
            field("status", session.status.name)
            field("savedAtMs", session.savedAtMs.toString())
            nullableField("planFingerprint", session.planFingerprint)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun matches(session: PersistedTaskSession): Boolean {
        val persisted = session.checkpointDigest ?: return false
        val expected = compute(session)
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            persisted.toByteArray(StandardCharsets.US_ASCII)
        )
    }

    private fun StringBuilder.field(name: String, value: String) {
        append(name.length).append(':').append(name)
        append('=').append(value.length).append(':').append(value).append(';')
    }

    private fun StringBuilder.nullableField(name: String, value: String?) {
        if (value == null) {
            field(name, "<null>")
        } else {
            field(name, value)
        }
    }
}

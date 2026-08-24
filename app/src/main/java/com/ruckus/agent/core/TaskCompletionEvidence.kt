package com.ruckus.agent.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Stable evidence digest for terminal COMPLETE checkpoints.
 *
 * This does not replace the checkpoint integrity digest. It gives a completed task a
 * dedicated proof identity bound to the exact request, plan fingerprint, verified step
 * count, terminal action, final observation, and recovery count that produced COMPLETE.
 */
object TaskCompletionEvidence {
    fun compute(session: PersistedTaskSession): String {
        val canonical = buildString {
            field("request", session.request)
            nullableField("planFingerprint", session.planFingerprint)
            field("currentStep", session.currentStep.toString())
            field("totalSteps", session.totalSteps.toString())
            nullableField("lastAction", session.lastAction)
            nullableField("lastScreenSummary", session.lastScreenSummary)
            field("recoveryAttempts", session.recoveryAttempts.toString())
            field("status", session.status.name)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun matches(session: PersistedTaskSession): Boolean {
        val persisted = session.completionEvidenceDigest ?: return false
        val expected = compute(session)
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            persisted.toByteArray(StandardCharsets.US_ASCII)
        )
    }

    fun shortId(session: PersistedTaskSession): String? =
        session.completionEvidenceDigest?.takeIf { it.isNotBlank() }?.take(12)

    private fun StringBuilder.field(name: String, value: String) {
        append(name.length).append(':').append(name)
        append('=').append(value.length).append(':').append(value).append(';')
    }

    private fun StringBuilder.nullableField(name: String, value: String?) {
        field(name, value ?: "<null>")
    }
}

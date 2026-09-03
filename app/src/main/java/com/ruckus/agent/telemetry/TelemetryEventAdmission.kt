package com.ruckus.agent.telemetry

/**
 * Pure admission boundary for local RUKUS telemetry events.
 *
 * This layer does not transmit, persist, or enrich data. It only decides whether
 * a caller-supplied event is structurally safe to hand to a later local sink.
 */
object TelemetryEventAdmission {
    private const val MAX_EVENT_NAME_BYTES = 64
    private const val MAX_ATTRIBUTES = 12
    private const val MAX_KEY_BYTES = 48
    private const val MAX_VALUE_BYTES = 160

    private val eventName = Regex("^[a-z][a-z0-9_]{0,63}$")
    private val attributeKey = Regex("^[a-z][a-z0-9_]{0,47}$")

    private val forbiddenKeyFragments = listOf(
        "command",
        "prompt",
        "clipboard",
        "password",
        "secret",
        "token",
        "email",
        "phone",
        "contact",
        "message",
        "url",
        "uri",
        "path",
        "filename",
        "package_name",
        "app_name",
        "accessibility_text",
        "screen_text"
    )

    data class Candidate(
        val name: String,
        val attributes: Map<String, String> = emptyMap()
    )

    data class Admitted(
        val name: String,
        val attributes: Map<String, String>
    )

    sealed interface Result {
        data class Accepted(val event: Admitted) : Result
        data class Rejected(val reason: String) : Result
    }

    fun admit(candidate: Candidate): Result {
        if (!candidate.name.isValidUtf8Sized(MAX_EVENT_NAME_BYTES) || !eventName.matches(candidate.name)) {
            return Result.Rejected("invalid event name")
        }
        if (candidate.attributes.size > MAX_ATTRIBUTES) {
            return Result.Rejected("too many attributes")
        }

        val normalized = linkedMapOf<String, String>()
        for ((rawKey, rawValue) in candidate.attributes.toSortedMap()) {
            val key = rawKey.trim()
            val value = rawValue.trim()

            if (key != rawKey || value != rawValue) {
                return Result.Rejected("leading or trailing whitespace is not allowed")
            }
            if (!key.isValidUtf8Sized(MAX_KEY_BYTES) || !attributeKey.matches(key)) {
                return Result.Rejected("invalid attribute key")
            }
            if (forbiddenKeyFragments.any { fragment -> key.contains(fragment) }) {
                return Result.Rejected("sensitive attribute category")
            }
            if (!value.isValidUtf8Sized(MAX_VALUE_BYTES) || value.isEmpty() || value.hasControlCharacter()) {
                return Result.Rejected("invalid attribute value")
            }
            if (normalized.put(key, value) != null) {
                return Result.Rejected("duplicate attribute key")
            }
        }

        return Result.Accepted(Admitted(candidate.name, normalized.toMap()))
    }

    private fun String.isValidUtf8Sized(maxBytes: Int): Boolean {
        if (hasControlCharacter()) return false
        val bytes = toByteArray(Charsets.UTF_8)
        return bytes.size in 1..maxBytes && bytes.toString(Charsets.UTF_8) == this
    }

    private fun String.hasControlCharacter(): Boolean = any { it.code < 0x20 || it.code == 0x7f }
}

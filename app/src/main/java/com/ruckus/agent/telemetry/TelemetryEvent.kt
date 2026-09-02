package com.ruckus.agent.telemetry

/**
 * Small, deterministic telemetry boundary for local event plumbing.
 *
 * Events are intentionally allow-listed and bounded before any platform exporter
 * or network transport is introduced. This keeps command, permission, onboarding,
 * and settings telemetry safe to queue without leaking arbitrary payloads.
 */
data class TelemetryEvent(
    val name: Name,
    val timestampElapsedRealtimeMs: Long,
    val attributes: Map<String, String> = emptyMap(),
) {
    enum class Name {
        COMMAND_STARTED,
        COMMAND_FINISHED,
        COMMAND_FAILED,
        PERMISSION_CHECKED,
        ONBOARDING_STEP_VIEWED,
        SETTING_CHANGED,
    }
}

object TelemetryEventBoundary {
    const val MAX_ATTRIBUTE_COUNT: Int = 12
    const val MAX_KEY_LENGTH: Int = 40
    const val MAX_VALUE_LENGTH: Int = 160

    fun sanitize(event: TelemetryEvent): TelemetryEvent {
        require(event.timestampElapsedRealtimeMs >= 0) {
            "timestampElapsedRealtimeMs must be non-negative"
        }
        require(event.attributes.size <= MAX_ATTRIBUTE_COUNT) {
            "too many telemetry attributes"
        }

        val sanitized = event.attributes.mapKeys { (key, _) ->
            require(key.isNotBlank() && key.length <= MAX_KEY_LENGTH) {
                "telemetry attribute keys must be non-blank and <= $MAX_KEY_LENGTH chars"
            }
            key
        }.mapValues { (_, value) ->
            require(value.length <= MAX_VALUE_LENGTH) {
                "telemetry attribute values must be <= $MAX_VALUE_LENGTH chars"
            }
            value
        }

        return event.copy(attributes = sanitized)
    }
}

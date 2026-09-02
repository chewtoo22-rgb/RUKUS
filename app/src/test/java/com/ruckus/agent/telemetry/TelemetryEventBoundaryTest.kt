package com.ruckus.agent.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TelemetryEventBoundaryTest {
    @Test
    fun preserves_allowlisted_event_and_attributes() {
        val event = TelemetryEvent(
            name = TelemetryEvent.Name.COMMAND_FINISHED,
            timestampElapsedRealtimeMs = 42,
            attributes = mapOf("command_id" to "abc", "result" to "success"),
        )

        assertEquals(event, TelemetryEventBoundary.sanitize(event))
    }

    @Test
    fun rejects_negative_timestamp() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEventBoundary.sanitize(
                TelemetryEvent(TelemetryEvent.Name.COMMAND_STARTED, -1),
            )
        }
    }

    @Test
    fun rejects_oversized_attribute_value() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEventBoundary.sanitize(
                TelemetryEvent(
                    name = TelemetryEvent.Name.SETTING_CHANGED,
                    timestampElapsedRealtimeMs = 1,
                    attributes = mapOf("setting" to "x".repeat(TelemetryEventBoundary.MAX_VALUE_LENGTH + 1)),
                ),
            )
        }
    }

    @Test
    fun rejects_attribute_count_overflow() {
        val attributes = (0..TelemetryEventBoundary.MAX_ATTRIBUTE_COUNT)
            .associate { "key_$it" to "value" }

        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEventBoundary.sanitize(
                TelemetryEvent(TelemetryEvent.Name.PERMISSION_CHECKED, 1, attributes),
            )
        }
    }
}

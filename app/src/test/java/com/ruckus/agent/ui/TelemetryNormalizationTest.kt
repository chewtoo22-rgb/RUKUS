package com.ruckus.agent.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryNormalizationTest {
    @Test
    fun batteryRejectsInvalidScaleAndClampsBounds() {
        assertEquals(0, TelemetryNormalization.batteryPercent(50, 0))
        assertEquals(0, TelemetryNormalization.batteryPercent(-1, 100))
        assertEquals(50, TelemetryNormalization.batteryPercent(50, 100))
        assertEquals(100, TelemetryNormalization.batteryPercent(150, 100))
    }

    @Test
    fun ratioPercentHandlesZeroAndImpossibleInputs() {
        assertEquals(0, TelemetryNormalization.ratioPercent(10, 0))
        assertEquals(0, TelemetryNormalization.ratioPercent(-50, 100))
        assertEquals(40, TelemetryNormalization.ratioPercent(40, 100))
        assertEquals(100, TelemetryNormalization.ratioPercent(150, 100))
    }

    @Test
    fun cpuPercentRejectsNonFiniteAndClampsOversubscription() {
        assertEquals(0, TelemetryNormalization.cpuPercent(Float.NaN, 8))
        assertEquals(0, TelemetryNormalization.cpuPercent(2f, 0))
        assertEquals(25, TelemetryNormalization.cpuPercent(2f, 8))
        assertEquals(100, TelemetryNormalization.cpuPercent(32f, 8))
    }

    @Test
    fun byteConversionNeverReportsNegativeCapacity() {
        assertEquals(0f, TelemetryNormalization.nonNegativeGb(-1), 0f)
        assertEquals(1f, TelemetryNormalization.nonNegativeGb(1_073_741_824L), 0f)
    }
}

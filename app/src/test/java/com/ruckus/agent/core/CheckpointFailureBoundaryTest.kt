package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CheckpointFailureBoundaryTest {
    @Test
    fun `successful execution report is preserved`() {
        val expected = ExecutionReport(ok = true, message = "done", completedSteps = 1, totalSteps = 1)

        assertEquals(expected, CheckpointFailureBoundary.execute { expected })
    }

    @Test
    fun `checkpoint persistence failure becomes controlled execution failure`() {
        val report = CheckpointFailureBoundary.execute {
            throw CheckpointPersistenceException(IllegalStateException("disk full"))
        }

        assertFalse(report.ok)
        assertEquals(CheckpointFailureBoundary.MESSAGE, report.message)
        assertFalse(report.needsConfirmation)
    }

    @Test
    fun `unrelated runtime failures are not swallowed`() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckpointFailureBoundary.execute {
                throw IllegalArgumentException("programming error")
            }
        }
    }
}

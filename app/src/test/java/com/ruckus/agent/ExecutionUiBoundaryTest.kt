package com.ruckus.agent

import com.ruckus.agent.core.ExecutionReport
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExecutionUiBoundaryTest {
    @Test
    fun successfulReportPassesThrough() {
        runBlocking {
            val expected = ExecutionReport(true, "done")

            val actual = ExecutionUiBoundary.run { expected }

            assertEquals(expected, actual)
        }
    }

    @Test
    fun recoverableExceptionBecomesFailedReport() {
        runBlocking {
            val report = ExecutionUiBoundary.run {
                throw IllegalStateException("android call failed")
            }

            assertFalse(report.ok)
            assertEquals("android call failed", report.message)
        }
    }

    @Test(expected = CancellationException::class)
    fun coroutineCancellationStillPropagates() {
        runBlocking {
            ExecutionUiBoundary.run {
                throw CancellationException("cancelled")
            }
        }
    }

    @Test(expected = AssertionError::class)
    fun fatalErrorStillPropagates() {
        runBlocking {
            ExecutionUiBoundary.run {
                throw AssertionError("fatal")
            }
        }
    }
}

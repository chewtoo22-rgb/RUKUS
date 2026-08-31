package com.ruckus.agent.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionAdmissionGateTest {
    @Test
    fun onlyOneLeaseCanBeHeldAtATime() {
        val gate = ExecutionAdmissionGate()

        assertTrue(gate.tryAcquire())
        assertTrue(gate.isHeld())
        assertFalse(gate.tryAcquire())

        gate.release()
        assertFalse(gate.isHeld())
        assertTrue(gate.tryAcquire())
        gate.release()
    }

    @Test
    fun concurrentCallersAdmitExactlyOneExecution() {
        val gate = ExecutionAdmissionGate()
        val workers = 24
        val pool = Executors.newFixedThreadPool(workers)
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val attempted = CountDownLatch(workers)
        val releaseWinner = CountDownLatch(1)
        val winners = AtomicInteger(0)

        repeat(workers) {
            pool.execute {
                ready.countDown()
                start.await()
                val acquired = gate.tryAcquire()
                if (acquired) winners.incrementAndGet()
                attempted.countDown()
                if (acquired) {
                    releaseWinner.await()
                    gate.release()
                }
            }
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(attempted.await(5, TimeUnit.SECONDS))
        assertEquals(1, winners.get())
        assertTrue(gate.isHeld())

        releaseWinner.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertFalse(gate.isHeld())
    }
}

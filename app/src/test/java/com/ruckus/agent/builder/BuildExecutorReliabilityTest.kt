package com.ruckus.agent.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class BuildExecutorReliabilityTest {
    @Test
    fun boundedLogRetainsOnlyNewestLines() {
        val log = BoundedBuildLog(3)
        listOf("one", "two", "three", "four", "five").forEach(log::append)

        assertEquals(listOf("three", "four", "five"), log.snapshot())
    }

    @Test
    fun hungGradleWrapperAndChildAreActuallyTimedOut() {
        assumeFalse(System.getProperty("os.name").lowercase().contains("windows"))

        val root = Files.createTempDirectory("rukus-builder-timeout").toFile()
        try {
            val wrapper = root.resolve("gradlew")
            // Exercise the harder process-tree case: a generated/custom wrapper may spawn a
            // child that inherits stdout. Timeout teardown must kill both and return promptly.
            wrapper.writeText("#!/bin/sh\necho before-hang\nsleep 30 &\nwait\n")
            assertTrue(wrapper.setExecutable(true))

            val job = BuildJob(
                projectId = "test-project",
                workspacePath = root.absolutePath,
                state = BuildJobState.BUILDING
            )

            val started = System.nanoTime()
            val result = BuildExecutor(timeout = 150, timeoutUnit = TimeUnit.MILLISECONDS)
                .assembleDebug(job)
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            assertEquals(BuildJobState.FAILED, result.state)
            assertTrue(result.log.contains("before-hang"))
            assertTrue(result.log.contains("Build timed out"))
            assertTrue("timeout should terminate the wrapper process tree promptly", elapsedMillis < 5_000)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nonExecutableWrapperFailsClosedBeforeLaunch() {
        assumeFalse(System.getProperty("os.name").lowercase().contains("windows"))

        val root = Files.createTempDirectory("rukus-builder-nonexec").toFile()
        try {
            val wrapper = root.resolve("gradlew")
            wrapper.writeText("#!/bin/sh\nexit 0\n")
            wrapper.setExecutable(false)

            val result = BuildExecutor(timeout = 1, timeoutUnit = TimeUnit.SECONDS).assembleDebug(
                BuildJob(
                    projectId = "test-project",
                    workspacePath = root.absolutePath,
                    state = BuildJobState.BUILDING
                )
            )

            assertEquals(BuildJobState.FAILED, result.state)
            assertTrue(result.log.contains("gradlew is not executable"))
        } finally {
            root.deleteRecursively()
        }
    }
}
